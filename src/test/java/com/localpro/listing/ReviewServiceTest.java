package com.localpro.listing;

import com.localpro.listing.dto.CreateReviewRequest;
import com.localpro.listing.dto.ReviewResponse;
import com.localpro.user.User;
import com.localpro.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock ServiceListingRepository listingRepository;
    @Mock UserRepository userRepository;
    @Mock ReviewMapper reviewMapper;
    @InjectMocks ReviewService reviewService;

    UUID clientId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();
    UUID listingId = UUID.randomUUID();

    User client;
    User provider;
    ServiceListing listing;

    @BeforeEach
    void setUp() {
        client = User.builder().id(clientId).firebaseUid("client").email("client@test.com").name("Client").build();
        provider = User.builder().id(providerId).firebaseUid("prov").email("prov@test.com").name("Provider").build();
        listing = ServiceListing.builder()
                .id(listingId)
                .provider(provider)
                .title("Service")
                .reviewCount(0)
                .rating(BigDecimal.ZERO)
                .build();
    }

    @Test
    void create_success_savesReviewAndUpdatesAggregates() {
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(reviewRepository.existsByListingIdAndClientId(listingId, clientId)).thenReturn(false);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        Review saved = Review.builder().id(UUID.randomUUID()).listing(listing).client(client).rating(5).build();
        when(reviewRepository.save(any())).thenReturn(saved);
        when(userRepository.save(provider)).thenReturn(provider);
        when(listingRepository.save(listing)).thenReturn(listing);

        Review result = reviewService.create(clientId, listingId, new CreateReviewRequest(5, "Great!"));

        assertThat(result).isSameAs(saved);
        assertThat(listing.getReviewCount()).isEqualTo(1);
        assertThat(listing.getRating()).isEqualByComparingTo("5.00");
        assertThat(provider.getReviewCount()).isEqualTo(1);
        assertThat(provider.getRating()).isEqualByComparingTo("5.00");
    }

    @Test
    void create_selfReview_throwsIllegalArgument() {
        listing = ServiceListing.builder().id(listingId).provider(client).title("Service").build();
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> reviewService.create(clientId, listingId, new CreateReviewRequest(5, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot review own listing");
    }

    @Test
    void create_duplicateReview_throwsIllegalArgument() {
        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(reviewRepository.existsByListingIdAndClientId(listingId, clientId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create(clientId, listingId, new CreateReviewRequest(4, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Already reviewed");
    }

    @Test
    void create_listingNotFound_throwsEntityNotFoundException() {
        when(listingRepository.findById(listingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(clientId, listingId, new CreateReviewRequest(3, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getByListing_returnsMappedPage() {
        Review review = Review.builder().id(UUID.randomUUID()).listing(listing).client(client).rating(4).build();
        ReviewResponse dto = new ReviewResponse(review.getId(), 4, null, clientId, "Client", null, null);
        when(reviewRepository.findByListingIdOrderByCreatedAtDesc(eq(listingId), any()))
                .thenReturn(new PageImpl<>(List.of(review)));
        when(reviewMapper.toResponse(review)).thenReturn(dto);

        Page<ReviewResponse> result = reviewService.getByListing(listingId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).rating()).isEqualTo(4);
    }

    @Test
    void create_providerRatingUpdatedAcrossMultipleListings() {
        // Provider already has 1 review (rating 5.0) from another listing.
        // After a new review with rating 3, incremental formula gives avg = (5*1+3)/2 = 4.00.
        provider.setReviewCount(1);
        provider.setRating(BigDecimal.valueOf(5.0));

        when(listingRepository.findById(listingId)).thenReturn(Optional.of(listing));
        when(reviewRepository.existsByListingIdAndClientId(listingId, clientId)).thenReturn(false);
        when(userRepository.findById(clientId)).thenReturn(Optional.of(client));
        Review saved = Review.builder().id(UUID.randomUUID()).listing(listing).client(client).rating(3).build();
        when(reviewRepository.save(any())).thenReturn(saved);
        when(userRepository.save(provider)).thenReturn(provider);
        when(listingRepository.save(listing)).thenReturn(listing);

        reviewService.create(clientId, listingId, new CreateReviewRequest(3, null));

        assertThat(provider.getReviewCount()).isEqualTo(2);
        assertThat(provider.getRating()).isEqualByComparingTo("4.00");
    }
}
