package com.localpro.listing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceListingRepository extends JpaRepository<ServiceListing, UUID> {

    Page<ServiceListing> findByProviderIdAndStatusNot(UUID providerId, ListingStatus status, Pageable pageable);

    Optional<ServiceListing> findByIdAndProviderId(UUID id, UUID providerId);

    @Query("""
            SELECT sl FROM ServiceListing sl
            LEFT JOIN FETCH sl.category
            LEFT JOIN FETCH sl.provider
            LEFT JOIN FETCH sl.photos
            WHERE sl.id = :id
            """)
    Optional<ServiceListing> findByIdWithDetails(@Param("id") UUID id);

    @Query(value = """
            SELECT sl.* FROM service_listings sl
            WHERE ST_DWithin(sl.location, ST_MakePoint(:lng, :lat)::geography, :radiusMeters)
            AND (:categoryId IS NULL OR sl.category_id = CAST(:categoryId AS uuid))
            AND sl.status = 'ACTIVE'
            AND sl.is_visible_on_map = true
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
            AND sl.is_visible_on_map = true
            """, nativeQuery = true)
    long countNearby(@Param("lat") double lat,
                     @Param("lng") double lng,
                     @Param("radiusMeters") double radiusMeters,
                     @Param("categoryId") String categoryId);

    @Modifying
    @Query("UPDATE ServiceListing sl SET sl.viewCount = sl.viewCount + 1 WHERE sl.id = :id")
    void incrementViewCount(@Param("id") UUID id);

    @Query(value = """
            SELECT sl FROM ServiceListing sl
            LEFT JOIN FETCH sl.provider
            LEFT JOIN FETCH sl.category
            WHERE sl.status = 'ACTIVE'
            AND sl.isVisibleOnMap = true
            AND (:query IS NULL OR LOWER(sl.title) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(sl.description) LIKE LOWER(CONCAT('%', :query, '%')))
            AND (:categoryId IS NULL OR sl.category.id = :categoryId)
            AND (:priceType IS NULL OR sl.priceType = :priceType)
            AND (:minPrice IS NULL OR sl.price >= :minPrice)
            AND (:maxPrice IS NULL OR sl.price <= :maxPrice)
            AND (:city IS NULL OR LOWER(sl.city) LIKE LOWER(CONCAT('%', :city, '%')))
            ORDER BY
            CASE WHEN :sortBy = 'price_asc' THEN sl.price END ASC,
            CASE WHEN :sortBy = 'price_desc' THEN sl.price END DESC,
            CASE WHEN :sortBy = 'rating' THEN sl.rating END DESC,
            CASE WHEN :sortBy = 'popular' THEN sl.viewCount END DESC,
            CASE WHEN :sortBy = 'newest' THEN sl.createdAt END DESC
            """,
            countQuery = """
            SELECT COUNT(sl) FROM ServiceListing sl
            WHERE sl.status = 'ACTIVE'
            AND sl.isVisibleOnMap = true
            AND (:query IS NULL OR LOWER(sl.title) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(sl.description) LIKE LOWER(CONCAT('%', :query, '%')))
            AND (:categoryId IS NULL OR sl.category.id = :categoryId)
            AND (:priceType IS NULL OR sl.priceType = :priceType)
            AND (:minPrice IS NULL OR sl.price >= :minPrice)
            AND (:maxPrice IS NULL OR sl.price <= :maxPrice)
            AND (:city IS NULL OR LOWER(sl.city) LIKE LOWER(CONCAT('%', :city, '%')))
            """)
    Page<ServiceListing> search(
            @Param("query") String query,
            @Param("categoryId") UUID categoryId,
            @Param("priceType") PriceType priceType,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("city") String city,
            @Param("sortBy") String sortBy,
            Pageable pageable);

    List<ServiceListing> findTop10ByStatusAndIsVisibleOnMapTrueOrderByViewCountDesc(ListingStatus status);

    List<ServiceListing> findTop10ByStatusAndIsVerifiedTrueAndIsVisibleOnMapTrueOrderByCreatedAtDesc(ListingStatus status);

    Page<ServiceListing> findByCategory_IdAndStatusAndIsVisibleOnMapTrue(UUID categoryId, ListingStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ServiceListing sl SET sl.reviewCount = :count, sl.rating = :avgRating WHERE sl.id = :id")
    void updateRatingStats(@Param("id") UUID id, @Param("count") int count, @Param("avgRating") BigDecimal avgRating);
}
