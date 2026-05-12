package com.localpro.booking;

import com.localpro.AbstractIntegrationTest;
import com.localpro.booking.dto.BookingResponse;
import com.localpro.booking.dto.CreateBookingRequest;
import com.localpro.listing.Category;
import com.localpro.listing.CategoryRepository;
import com.localpro.listing.ListingStatus;
import com.localpro.listing.PriceType;
import com.localpro.listing.ServiceListing;
import com.localpro.listing.ServiceListingRepository;
import com.localpro.user.User;
import com.localpro.user.UserRepository;
import com.localpro.user.UserRole;
import com.localpro.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ServiceListingRepository listingRepository;

    private User devUser;
    private UUID listingId;

    @BeforeEach
    void setUp() {
        // Ensure dev user exists before creating provider so getOrCreateDevUser() returns it
        devUser = userService.getOrCreateDevUser();

        User provider = userRepository.save(User.builder()
                .firebaseUid("test-provider-" + UUID.randomUUID())
                .email("provider-" + UUID.randomUUID() + "@test.com")
                .name("Test Provider")
                .role(UserRole.PROVIDER)
                .build());

        Category category = categoryRepository.findAll().get(0);

        ServiceListing listing = listingRepository.save(ServiceListing.builder()
                .provider(provider)
                .category(category)
                .title("Test Service for Booking")
                .price(BigDecimal.valueOf(100))
                .priceType(PriceType.PER_SERVICE)
                .status(ListingStatus.ACTIVE)
                .address("Test St 1")
                .city("Kyiv")
                .build());

        listingId = listing.getId();
    }

    @Test
    void createBooking_withCash_returnsPendingPaymentStatus() {
        CreateBookingRequest request = new CreateBookingRequest(
                listingId,
                Instant.now().plus(1, ChronoUnit.DAYS),
                PaymentType.CASH,
                CalendarType.IN_APP,
                BigDecimal.valueOf(100),
                "Please bring cleaning supplies"
        );

        ResponseEntity<BookingResponse> response = restTemplate.exchange(
                "/api/bookings",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BookingResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.listing().id()).isEqualTo(listingId);
        assertThat(body.customer().id()).isEqualTo(devUser.getId());
        assertThat(body.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(body.payment().type()).isEqualTo(PaymentType.CASH);
        assertThat(body.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(body.payment().totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void createBooking_withCreditCard_returnsPaidPaymentStatus() {
        CreateBookingRequest request = new CreateBookingRequest(
                listingId,
                Instant.now().plus(2, ChronoUnit.DAYS),
                PaymentType.CREDIT_CARD,
                CalendarType.IN_APP,
                BigDecimal.valueOf(100),
                null
        );

        ResponseEntity<BookingResponse> response = restTemplate.exchange(
                "/api/bookings",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                BookingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        BookingResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(body.payment().type()).isEqualTo(PaymentType.CREDIT_CARD);
        assertThat(body.payment().status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void createBooking_withoutAuth_returns401() {
        CreateBookingRequest request = new CreateBookingRequest(
                listingId,
                Instant.now().plus(1, ChronoUnit.DAYS),
                PaymentType.CASH,
                null,
                BigDecimal.valueOf(100),
                null
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/bookings",
                HttpMethod.POST,
                new HttpEntity<>(request),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createBooking_withPastDate_returns400() {
        CreateBookingRequest request = new CreateBookingRequest(
                listingId,
                Instant.now().minus(1, ChronoUnit.DAYS),
                PaymentType.CASH,
                null,
                BigDecimal.valueOf(100),
                null
        );

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/bookings",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
