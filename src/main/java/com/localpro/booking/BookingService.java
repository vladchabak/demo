package com.localpro.booking;

import com.github.benmanes.caffeine.cache.Cache;
import com.localpro.booking.dto.*;
import com.localpro.listing.ListingStatus;
import com.localpro.listing.ServiceListing;
import com.localpro.listing.ServiceListingRepository;
import com.localpro.listing.ServicePhoto;
import com.localpro.notification.NotificationService;
import com.localpro.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ServiceListingRepository listingRepository;
    private final PaymentService paymentService;
    private final CalendarService calendarService;
    private final Cache<String, BookingResponse> idempotencyCache;
    private final NotificationService notificationService;

    public BookingResponse create(User customer, CreateBookingRequest req, String idempotencyKey) {
        log.info("Creating booking for listing {} by customer {}, paymentType: {}, scheduledAt: {}",
                req.listingId(), customer.getId(), req.paymentType(), req.scheduledAt());

        // Layer 2: Check idempotency cache
        if (idempotencyKey != null) {
            BookingResponse cached = idempotencyCache.getIfPresent(idempotencyKey);
            if (cached != null) {
                log.info("Idempotency hit for key {}, returning cached response", idempotencyKey);
                return cached;
            }
        }

        ServiceListing listing = listingRepository.findByIdWithDetails(req.listingId())
                .orElseThrow(() -> new EntityNotFoundException("Listing not found: " + req.listingId()));

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new IllegalArgumentException("Listing is not available for booking");
        }

        User provider = listing.getProvider();

        if (customer.getId().equals(provider.getId())) {
            throw new IllegalArgumentException("Cannot book your own listing");
        }

        BigDecimal amount = req.totalPrice() != null ? req.totalPrice() : listing.getPrice();

        // Layer 3: Database unique constraint guards
        // Layer 4: Service-layer guards (fast fail, good error message)
        boolean alreadyBooked = bookingRepository.existsActiveBooking(
                customer.getId(), req.listingId(), req.scheduledAt());
        if (alreadyBooked) {
            throw new IllegalArgumentException(
                    "You already have an active booking for this listing at this time");
        }

        boolean providerBusy = bookingRepository.existsProviderConflict(
                provider.getId(), req.scheduledAt());
        if (providerBusy) {
            throw new IllegalArgumentException(
                    "This provider is already booked at the requested time");
        }

        CalendarType calendarType = req.calendarType() != null ? req.calendarType() : CalendarType.IN_APP;
        CalendarLinks links = calendarService.generateLinks(calendarType, listing.getId(), req.scheduledAt());
        String calendarEventId = switch (calendarType) {
            case IN_APP -> null;
            case CALENDLY -> links.calendlyLink();
            case GOOGLE_CALENDAR -> links.googleCalendarLink();
        };

        // Step 1: Save PENDING booking first (safe order)
        Booking booking = Booking.builder()
                .listing(listing)
                .customer(customer)
                .provider(provider)
                .scheduledAt(req.scheduledAt())
                .paymentType(req.paymentType())
                .paymentStatus(PaymentStatus.PENDING)
                .totalPrice(amount)
                .notes(req.notes())
                .calendarType(calendarType)
                .calendarEventId(calendarEventId)
                .build();

        Booking saved = bookingRepository.save(booking);
        log.info("Booking {} saved with PENDING status", saved.getId());

        // Step 2: Process payment after save
        try {
            paymentService.processPayment(req.paymentType(), amount);

            // Step 3: Mark as PAID (CASH remains PENDING for payment on arrival)
            PaymentStatus finalStatus = req.paymentType() == PaymentType.CASH
                    ? PaymentStatus.PENDING : PaymentStatus.PAID;
            saved.setPaymentStatus(finalStatus);
            saved = bookingRepository.save(saved);
            log.info("Booking {} payment processed, status: {}", saved.getId(), finalStatus);
        } catch (Exception e) {
            // Step 4: On failure, mark as CANCELLED and roll back
            saved.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(saved);
            log.error("Payment failed for booking {}, marked as CANCELLED", saved.getId(), e);
            throw e;
        }

        // Step 5: Send FCM notification to provider (async)
        if (provider.getFcmToken() != null) {
            notificationService.sendBookingCreatedNotification(
                    provider.getFcmToken(),
                    saved.getId(),
                    customer.getName(),
                    listing.getTitle());
        }

        BookingResponse response = toResponse(saved, customer);

        // Step 6: Cache response under idempotency key
        if (idempotencyKey != null) {
            idempotencyCache.put(idempotencyKey, response);
            log.info("Cached response for idempotency key {}", idempotencyKey);
        }

        log.info("Customer {} created booking {} for listing {}", customer.getId(), saved.getId(), listing.getId());
        return response;
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(User user, BookingStatus status, Pageable pageable) {
        log.info("=== [BookingService.getMyBookings] called for user: {} with status: {}", user.getId(), status);
        return bookingRepository.findPageByUserIdWithDetails(user.getId(), status, pageable)
                .map(b -> toResponse(b, user));
    }

    public BookingResponse cancel(UUID bookingId, User user, CancelBookingRequest req) {
        log.info("=== [BookingService.cancel] called for booking: {} by user: {}", bookingId, user.getId());

        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> {
                    log.warn("Booking not found for cancellation: {}", bookingId);
                    return new EntityNotFoundException("Booking not found: " + bookingId);
                });

        boolean isCustomer = booking.getCustomer().getId().equals(user.getId());
        boolean isProvider = booking.getProvider().getId().equals(user.getId());

        if (!isCustomer && !isProvider) {
            log.warn("Unauthorized cancel attempt on booking {} by user {}", bookingId, user.getId());
            throw new AccessDeniedException("Not authorized to cancel this booking");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.COMPLETED) {
            log.warn("Cannot cancel booking {} with status: {}", bookingId, booking.getStatus());
            throw new IllegalArgumentException("Cannot cancel a " + booking.getStatus().name().toLowerCase() + " booking");
        }

        // Customer can only cancel CONFIRMED booking if ≥2h before scheduledAt
        if (isCustomer && booking.getStatus() == BookingStatus.CONFIRMED) {
            Instant twoHoursFromNow = Instant.now().plus(2, ChronoUnit.HOURS);
            if (booking.getScheduledAt().isBefore(twoHoursFromNow)) {
                throw new IllegalArgumentException(
                        "Cannot cancel confirmed booking less than 2 hours before scheduled time");
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(req.reason());
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            booking.setPaymentStatus(PaymentStatus.REFUNDED);
            log.info("Refund issued for booking {}", bookingId);
        }

        Booking saved = bookingRepository.save(booking);
        log.info("Booking {} cancelled by user {} with reason: {}", bookingId, user.getId(), req.reason());
        return toResponse(saved, user);
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(UUID bookingId, User user) {
        log.info("=== [BookingService.getById] called for booking: {} by user: {}", bookingId, user.getId());

        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        boolean isCustomer = booking.getCustomer().getId().equals(user.getId());
        boolean isProvider = booking.getProvider().getId().equals(user.getId());

        if (!isCustomer && !isProvider) {
            throw new AccessDeniedException("Not authorized to view this booking");
        }

        return toResponse(booking, user);
    }

    public BookingResponse complete(UUID bookingId, User user) {
        log.info("=== [BookingService.complete] called for booking: {} by provider: {}", bookingId, user.getId());

        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found: " + bookingId));

        if (!booking.getProvider().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the provider can complete this booking");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only CONFIRMED bookings can be completed");
        }

        booking.setStatus(BookingStatus.COMPLETED);
        Booking saved = bookingRepository.save(booking);
        log.info("Booking {} marked as COMPLETED by provider {}", bookingId, user.getId());
        return toResponse(saved, user);
    }

    public BookingResponse confirm(UUID bookingId, User user) {
        log.info("=== [BookingService.confirm] called for booking: {} by provider: {}", bookingId, user.getId());

        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> {
                    log.warn("Booking not found for confirmation: {}", bookingId);
                    return new EntityNotFoundException("Booking not found: " + bookingId);
                });

        if (!booking.getProvider().getId().equals(user.getId())) {
            log.warn("Non-provider {} attempted to confirm booking {}", user.getId(), bookingId);
            throw new AccessDeniedException("Only the provider can confirm this booking");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            log.warn("Cannot confirm booking {} with status: {}", bookingId, booking.getStatus());
            throw new IllegalArgumentException("Only PENDING bookings can be confirmed");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);
        log.info("Booking {} confirmed by provider {}", bookingId, user.getId());
        return toResponse(saved, user);
    }

    private BookingResponse toResponse(Booking booking, User caller) {
        CalendarLinks links = calendarService.generateLinks(
                booking.getCalendarType(),
                booking.getListing().getId(),
                booking.getScheduledAt()
        );

        ServiceListing listing = booking.getListing();
        String photoUrl = listing.getPhotos().isEmpty()
                ? null
                : listing.getPhotos().get(0).getUrl();

        BookingListingInfo listingInfo = new BookingListingInfo(
                listing.getId(),
                listing.getTitle(),
                listing.getAddress(),
                listing.getCity(),
                listing.getPrice(),
                listing.getPriceType(),
                photoUrl
        );

        User provider = booking.getProvider();
        BookingProviderInfo providerInfo = new BookingProviderInfo(
                provider.getId(),
                provider.getName(),
                provider.getAvatarUrl(),
                provider.getPhone()
        );

        User customer = booking.getCustomer();
        BookingCustomerInfo customerInfo = new BookingCustomerInfo(
                customer.getId(),
                customer.getName()
        );

        BookingPaymentInfo paymentInfo = new BookingPaymentInfo(
                booking.getPaymentType(),
                booking.getPaymentStatus(),
                booking.getTotalPrice()
        );

        BookingCalendarInfo calendarInfo = new BookingCalendarInfo(
                booking.getCalendarType(),
                links.calendlyLink(),
                links.googleCalendarLink()
        );

        BookingActions actions = computeActions(booking, caller);

        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getScheduledAt(),
                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                listingInfo,
                providerInfo,
                customerInfo,
                paymentInfo,
                calendarInfo,
                booking.getNotes(),
                booking.getCancellationReason(),
                actions
        );
    }

    private BookingActions computeActions(Booking booking, User caller) {
        boolean isCustomer = booking.getCustomer().getId().equals(caller.getId());
        boolean isProvider = booking.getProvider().getId().equals(caller.getId());
        BookingStatus status = booking.getStatus();

        boolean canCancel = switch (status) {
            case PENDING -> isCustomer || isProvider;
            case CONFIRMED -> {
                if (isProvider) yield true;
                if (isCustomer) {
                    Instant twoHoursFromNow = Instant.now().plus(2, ChronoUnit.HOURS);
                    yield booking.getScheduledAt().isAfter(twoHoursFromNow);
                }
                yield false;
            }
            default -> false;
        };

        boolean canConfirm = isProvider && status == BookingStatus.PENDING;

        return new BookingActions(canCancel, canConfirm);
    }
}
