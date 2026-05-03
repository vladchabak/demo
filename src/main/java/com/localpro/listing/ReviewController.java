package com.localpro.listing;

import com.localpro.auth.CurrentUser;
import com.localpro.listing.dto.CreateReviewRequest;
import com.localpro.listing.dto.ReviewResponse;
import com.localpro.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;

    @PostMapping("/{listingId}/reviews")
    public ResponseEntity<ReviewResponse> create(@CurrentUser User user,
                                                 @PathVariable UUID listingId,
                                                 @Valid @RequestBody CreateReviewRequest request) {
        Review review = reviewService.create(user.getId(), listingId, request);
        return ResponseEntity.status(201).body(reviewMapper.toResponse(review));
    }

    @GetMapping("/{listingId}/reviews")
    public ResponseEntity<Page<ReviewResponse>> getByListing(
            @PathVariable UUID listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.getByListing(listingId, PageRequest.of(page, size)));
    }
}
