package com.localpro.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceListingRepository extends JpaRepository<ServiceListing, UUID> {

    Page<ServiceListing> findByProviderIdAndStatusNot(UUID providerId, ListingStatus status, Pageable pageable);

    Optional<ServiceListing> findByIdAndProviderId(UUID id, UUID providerId);

    @Query(value = """
            SELECT sl.* FROM service_listings sl
            WHERE ST_DWithin(sl.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
            AND (:categoryId IS NULL OR sl.category_id = CAST(:categoryId AS uuid))
            AND sl.status = 'ACTIVE'
            ORDER BY ST_Distance(sl.location, ST_MakePoint(:lng, :lat)::geography)
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<ServiceListing> findNearby(@Param("lat") double lat,
                                    @Param("lng") double lng,
                                    @Param("radiusMeters") double radiusMeters,
                                    @Param("categoryId") String categoryId,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    @Query(value = """
            SELECT COUNT(*) FROM service_listings sl
            WHERE ST_DWithin(sl.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
            AND (:categoryId IS NULL OR sl.category_id = CAST(:categoryId AS uuid))
            AND sl.status = 'ACTIVE'
            """, nativeQuery = true)
    long countNearby(@Param("lat") double lat,
                     @Param("lng") double lng,
                     @Param("radiusMeters") double radiusMeters,
                     @Param("categoryId") String categoryId);
}
