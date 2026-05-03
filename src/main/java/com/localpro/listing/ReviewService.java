package com.localpro.listing;

import com.localpro.listing.dto.CreateReviewRequest;
import com.localpro.listing.dto.ReviewResponse;
import com.localpro.user.User;
import com.localpro.user.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

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

        double avg = reviewRepository.findAverageRatingByListingId(listingId).orElse(0.0);
        long count = reviewRepository.countByListingId(listingId);
        listing.setRating(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
        listing.setReviewCount((int) count);
        listingRepository.save(listing);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getByListing(UUID listingId, Pageable pageable) {
        return reviewRepository.findByListingIdOrderByCreatedAtDesc(listingId, pageable)
                .map(reviewMapper::toResponse);
    }
}
