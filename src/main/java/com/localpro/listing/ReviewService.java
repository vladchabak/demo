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
        ServiceListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new EntityNotFoundException("Listing not found: " + listingId));

        if (listing.getProvider().getId().equals(clientId)) {
            throw new IllegalArgumentException("Cannot review own listing");
        }
        if (reviewRepository.existsByListingIdAndClientId(listingId, clientId)) {
            throw new IllegalArgumentException("Already reviewed this listing");
        }

        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + clientId));

        Review review = Review.builder()
                .listing(listing)
                .client(client)
                .rating(req.rating())
                .comment(req.comment())
                .build();
        Review saved = reviewRepository.save(review);
        log.info("User {} submitted review (rating={}) for listing {}", clientId, req.rating(), listingId);

        // Update listing aggregates
        double listingAvg = reviewRepository.findAverageRatingByListingId(listingId).orElse(0.0);
        long listingCount = reviewRepository.countByListingId(listingId);
        listing.setRating(BigDecimal.valueOf(listingAvg).setScale(2, RoundingMode.HALF_UP));
        listing.setReviewCount((int) listingCount);
        listingRepository.save(listing);

        // Update provider aggregates
        UUID providerId = listing.getProvider().getId();
        double providerAvg = reviewRepository.findAverageRatingByProviderId(providerId).orElse(0.0);
        long providerCount = reviewRepository.countByProviderId(providerId);
        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new EntityNotFoundException("Provider not found: " + providerId));
        provider.setRating(BigDecimal.valueOf(providerAvg).setScale(2, RoundingMode.HALF_UP));
        provider.setReviewCount((int) providerCount);
        userRepository.save(provider);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getByListing(UUID listingId, Pageable pageable) {
        return reviewRepository.findByListingIdOrderByCreatedAtDesc(listingId, pageable)
                .map(reviewMapper::toResponse);
    }
}
