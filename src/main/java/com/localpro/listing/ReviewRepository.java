package com.localpro.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByListingIdOrderByCreatedAtDesc(UUID listingId, Pageable pageable);

    boolean existsByListingIdAndClientId(UUID listingId, UUID clientId);

    // Used by RatingReconciliationJob (not yet implemented) — kept for future reconciliation safety net
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.listing.id = :listingId")
    Optional<Double> findAverageRatingByListingId(@Param("listingId") UUID listingId);

    long countByListingId(UUID listingId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.listing.provider.id = :providerId")
    Optional<Double> findAverageRatingByProviderId(@Param("providerId") UUID providerId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.listing.provider.id = :providerId")
    long countByProviderId(@Param("providerId") UUID providerId);

    // Returns [listingId (UUID), count (Long), avgRating (Double)] for every listing that has reviews.
    @Query("SELECT r.listing.id, COUNT(r), AVG(r.rating) FROM Review r GROUP BY r.listing.id")
    List<Object[]> findRatingStatsByListing();
}
