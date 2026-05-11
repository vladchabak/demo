package com.localpro.booking;

import com.localpro.booking.dto.BookingResponse;
import com.localpro.booking.dto.CalendarLinks;
import com.localpro.booking.dto.CreateBookingRequest;
import com.localpro.listing.ListingStatus;
import com.localpro.listing.ServiceListing;
import com.localpro.listing.ServiceListingRepository;
import com.localpro.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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

    public BookingResponse create(User customer, CreateBookingRequest req) {
        log.info("Creating booking for listing {} by customer {}, paymentType: {}, scheduledAt: {}",
                req.listingId(), customer.getId(), req.paymentType(), req.scheduledAt());

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
        paymentService.processPayment(req.paymentType(), amount);

        // CASH is paid on arrival; CREDIT_CARD and BONUSES are charged immediately (mock)
        PaymentStatus paymentStatus = req.paymentType() == PaymentType.CASH
                ? PaymentStatus.PENDING : PaymentStatus.PAID;

        CalendarType calendarType = req.calendarType() != null ? req.calendarType() : CalendarType.IN_APP;
        CalendarLinks links = calendarService.generateLinks(calendarType, listing.getId(), req.scheduledAt());
        String calendarEventId = switch (calendarType) {
            case IN_APP -> null;
            case CALENDLY -> links.calendlyLink();
            case GOOGLE_CALENDAR -> links.googleCalendarLink();
        };

        Booking booking = Booking.builder()
                .listing(listing)
                .customer(customer)
                .provider(provider)
                .scheduledAt(req.scheduledAt())
                .paymentType(req.paymentType())
                .paymentStatus(paymentStatus)
                .totalPrice(amount)
                .notes(req.notes())
                .calendarType(calendarType)
                .calendarEventId(calendarEventId)
                .build();

        Booking saved = bookingRepository.save(booking);
        log.info("Customer {} created booking {} for listing {}", customer.getId(), saved.getId(), listing.getId());
        return toResponse(saved, links);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(User user) {
        log.info("=== [BookingService.getMyBookings] called for user: {}", user.getId());
        List<BookingResponse> bookings = bookingRepository.findAllByUserIdWithDetails(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        log.info("Found {} bookings for user {}", bookings.size(), user.getId());
        return bookings;
    }

    public BookingResponse cancel(UUID bookingId, User user) {
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

        booking.setStatus(BookingStatus.CANCELLED);
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            booking.setPaymentStatus(PaymentStatus.REFUNDED);
            log.info("Refund issued for booking {}", bookingId);
        }

        Booking saved = bookingRepository.save(booking);
        log.info("Booking {} cancelled by user {}", bookingId, user.getId());
        return toResponse(saved);
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
        return toResponse(saved);
    }

    private BookingResponse toResponse(Booking booking) {
        CalendarLinks links = calendarService.generateLinks(
                booking.getCalendarType(),
                booking.getListing().getId(),
                booking.getScheduledAt()
        );
        return toResponse(booking, links);
    }

    private BookingResponse toResponse(Booking booking, CalendarLinks links) {
        return new BookingResponse(
                booking.getId(),
                booking.getListing().getId(),
                booking.getListing().getTitle(),
                booking.getCustomer().getId(),
                booking.getCustomer().getName(),
                booking.getProvider().getId(),
                booking.getProvider().getName(),
                booking.getStatus(),
                booking.getScheduledAt(),
                booking.getPaymentType(),
                booking.getPaymentStatus(),
                booking.getTotalPrice(),
                booking.getNotes(),
                booking.getCalendarType(),
                booking.getCalendarEventId(),
                links.calendlyLink(),
                links.googleCalendarLink(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}
