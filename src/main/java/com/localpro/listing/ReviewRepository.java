package com.localpro.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByListingIdOrderByCreatedAtDesc(UUID listingId, Pageable pageable);

    boolean existsByListingIdAndClientId(UUID listingId, UUID clientId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.listing.id = :listingId")
    Optional<Double> findAverageRatingByListingId(@Param("listingId") UUID listingId);

    long countByListingId(UUID listingId);
}
