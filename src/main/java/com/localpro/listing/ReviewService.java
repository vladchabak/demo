package com.localpro.listing;

import com.localpro.listing.dto.CreateReviewRequest;
import com.localpro.listing.dto.ReviewResponse;
import com.localpro.user.User;
import com.localpro.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ServiceListingRepository listingRepository;
    private final UserRepository userRepository;
    private final ReviewMapper reviewMapper;

    public Review create(UUID clientId, UUID listingId, CreateReviewRequest req) {
        log.info("=== [ReviewService.create] called by user: {} for listing: {}, rating: {}",
                clientId, listingId, req.rating());

        ServiceListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> {
                    log.warn("Listing not found for review: {}", listingId);
                    return new EntityNotFoundException("Listing not found: " + listingId);
                });

        if (listing.getProvider().getId().equals(clientId)) {
            log.warn("User {} attempted to review own listing {}", clientId, listingId);
            throw new IllegalArgumentException("Cannot review own listing");
        }
        if (reviewRepository.existsByListingIdAndClientId(listingId, clientId)) {
            log.warn("User {} already reviewed listing {}", clientId, listingId);
            throw new IllegalArgumentException("Already reviewed this listing");
        }

        User client = userRepository.findById(clientId)
                .orElseThrow(() -> {
                    log.warn("User not found for review: {}", clientId);
                    return new EntityNotFoundException("User not found: " + clientId);
                });

        Review review = Review.builder()
                .listing(listing)
                .client(client)
                .rating(req.rating())
                .comment(req.comment())
                .build();
        Review saved = reviewRepository.save(review);
        log.info("User {} submitted review (rating={}) for listing {}", clientId, req.rating(), listingId);

        // Update listing aggregates using incremental formula
        int oldListingCount = listing.getReviewCount();
        BigDecimal oldListingRating = listing.getRating() != null ? listing.getRating() : BigDecimal.ZERO;
        BigDecimal newListingRating = oldListingCount == 0
            ? BigDecimal.valueOf(req.rating())
            : oldListingRating
                .multiply(BigDecimal.valueOf(oldListingCount))
                .add(BigDecimal.valueOf(req.rating()))
                .divide(BigDecimal.valueOf(oldListingCount + 1), 2, RoundingMode.HALF_UP);
        listing.setRating(newListingRating);
        listing.setReviewCount(oldListingCount + 1);
        listingRepository.save(listing);

        // Update provider aggregates using same incremental formula
        User provider = listing.getProvider();
        int oldProviderCount = provider.getReviewCount();
        BigDecimal oldProviderRating = provider.getRating() != null ? provider.getRating() : BigDecimal.ZERO;
        BigDecimal newProviderRating = oldProviderCount == 0
            ? BigDecimal.valueOf(req.rating())
            : oldProviderRating
                .multiply(BigDecimal.valueOf(oldProviderCount))
                .add(BigDecimal.valueOf(req.rating()))
                .divide(BigDecimal.valueOf(oldProviderCount + 1), 2, RoundingMode.HALF_UP);
        provider.setRating(newProviderRating);
        provider.setReviewCount(oldProviderCount + 1);
        userRepository.save(provider);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getByListing(UUID listingId, Pageable pageable) {
        log.info("=== [ReviewService.getByListing] called for listing: {}, page: {}",
                listingId, pageable.getPageNumber());
        Page<ReviewResponse> reviews = reviewRepository.findByListingIdOrderByCreatedAtDesc(listingId, pageable)
                .map(reviewMapper::toResponse);
        log.info("Found {} reviews for listing {}", reviews.getNumberOfElements(), listingId);
        return reviews;
    }
}
