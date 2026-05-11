package com.localpro.listing;

import com.localpro.listing.dto.ListingRequest;
import com.localpro.listing.dto.ListingResponse;
import com.localpro.listing.dto.NearbyListingResponse;
import com.localpro.listing.dto.UpdateListingRequest;
import com.localpro.user.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ListingService {

    private final ServiceListingRepository listingRepository;
    private final CategoryRepository categoryRepository;
    private final ListingMapper listingMapper;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public ServiceListing create(User provider, ListingRequest req) {
        log.info("=== [ListingService.create] called by provider: {}, title: {}, category: {}",
                provider.getId(), req.title(), req.categoryId());

        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + req.categoryId()));

        ServiceListing listing = ServiceListing.builder()
                .provider(provider)
                .category(category)
                .title(req.title())
                .description(req.description())
                .price(req.price())
                .priceType(req.priceType() != null ? req.priceType() : PriceType.PER_SERVICE)
                .address(req.address())
                .city(req.city())
                .location(buildPoint(req.latitude(), req.longitude()))
                .customQuestions(req.customQuestions() != null ? req.customQuestions() : new ArrayList<>())
                .build();

        ServiceListing saved = listingRepository.save(listing);

        // Handle photo URLs
        if (req.photoUrls() != null && !req.photoUrls().isEmpty()) {
            for (int i = 0; i < req.photoUrls().size(); i++) {
                ServicePhoto photo = ServicePhoto.builder()
                        .listing(saved)
                        .url(req.photoUrls().get(i))
                        .sortOrder(i)
                        .build();
                saved.getPhotos().add(photo);
            }
            listingRepository.save(saved);
        }

        log.info("Provider {} created listing {} with {} photos", provider.getId(), saved.getId(),
                req.photoUrls() != null ? req.photoUrls().size() : 0);
        return saved;
    }

    public ServiceListing update(UUID providerId, UUID listingId, UpdateListingRequest req) {
        log.info("=== [ListingService.update] called for listing: {} by provider: {}",
                listingId, providerId);

        ServiceListing listing = listingRepository.findByIdAndProviderId(listingId, providerId)
                .orElseThrow(() -> new EntityNotFoundException("Listing not found: " + listingId));

        if (req.title() != null) listing.setTitle(req.title());
        if (req.description() != null) listing.setDescription(req.description());
        if (req.price() != null) listing.setPrice(req.price());
        if (req.priceType() != null) listing.setPriceType(req.priceType());
        if (req.address() != null) listing.setAddress(req.address());
        if (req.city() != null) listing.setCity(req.city());
        if (req.status() != null) listing.setStatus(req.status());
        if (req.categoryId() != null) {
            listing.setCategory(categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found: " + req.categoryId())));
        }
        Point point = buildPoint(req.lat(), req.lng());
        if (point != null) listing.setLocation(point);

        ServiceListing updated = listingRepository.save(listing);
        log.info("Listing {} updated successfully", listingId);
        return updated;
    }

    public void delete(UUID providerId, UUID listingId) {
        ServiceListing listing = listingRepository.findByIdAndProviderId(listingId, providerId)
                .orElseThrow(() -> new EntityNotFoundException("Listing not found: " + listingId));
        listing.setStatus(ListingStatus.DELETED);
        listingRepository.save(listing);
        log.info("Provider {} deleted listing {}", providerId, listingId);
    }

    public ServiceListing verify(UUID listingId) {
        log.info("=== [ListingService.verify] called for listing: {}", listingId);
        ServiceListing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> {
                    log.warn("Listing not found for verification: {}", listingId);
                    return new EntityNotFoundException("Listing not found: " + listingId);
                });
        listing.setVerified(true);
        listing.setVerifiedAt(LocalDateTime.now());
        listing.setVisibleOnMap(true);
        ServiceListing saved = listingRepository.save(listing);
        log.info("Listing {} verified successfully", listingId);
        return saved;
    }

    public ServiceListing getById(UUID id) {
        listingRepository.incrementViewCount(id);
        return listingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> {
                    log.warn("Listing not found: {}", id);
                    return new EntityNotFoundException("Listing not found: " + id);
                });
    }

    @Transactional(readOnly = true)
    public Page<ServiceListing> getByProvider(UUID providerId, Pageable pageable) {
        log.info("=== [ListingService.getByProvider] called for provider: {} page: {}",
                providerId, pageable.getPageNumber());
        return listingRepository.findByProviderIdAndStatusNot(providerId, ListingStatus.DELETED, pageable);
    }

    @Transactional(readOnly = true)
    public Page<NearbyListingResponse> findNearby(double lat, double lng, double radiusKm,
                                                   String categoryId, int page, int size) {
        log.info("=== [ListingService.findNearby] called with lat: {}, lng: {}, radius: {}km, category: {}, page: {}",
                lat, lng, radiusKm, categoryId, page);

        double radiusMeters = radiusKm * 1000;
        int offset = page * size;

        List<ServiceListing> listings = listingRepository.findNearby(lat, lng, radiusMeters, categoryId, size, offset);
        long total = listingRepository.countNearby(lat, lng, radiusMeters, categoryId);

        log.info("Found {} nearby listings (total: {} in radius)", listings.size(), total);

        List<NearbyListingResponse> results = listings.stream()
                .map(listing -> toNearbyResponse(listing, lat, lng))
                .toList();

        return new PageImpl<>(results, PageRequest.of(page, size), total);
    }

    private NearbyListingResponse toNearbyResponse(ServiceListing listing, double queryLat, double queryLng) {
        ListingResponse base = listingMapper.toResponse(listing);
        double dist = listing.getLocation() != null
                ? haversineMeters(listing.getLocation().getY(), listing.getLocation().getX(), queryLat, queryLng)
                : 0.0;
        Double pointLat = listing.getLocation() != null ? listing.getLocation().getY() : null;
        Double pointLng = listing.getLocation() != null ? listing.getLocation().getX() : null;

        return new NearbyListingResponse(
                base.id(), base.title(), base.description(),
                base.categoryId(), base.categoryName(),
                base.providerId(), base.providerName(), base.providerAvatarUrl(), base.providerRating(),
                base.price(), base.priceType(), base.address(), base.city(),
                base.status(), base.viewCount(), base.photoUrls(), base.createdAt(),
                dist, distanceLabel(dist), pointLat, pointLng
        );
    }

    private Point buildPoint(Double lat, Double lng) {
        if (lat == null || lng == null) return null;
        // JTS Coordinate is (x=longitude, y=latitude)
        return geometryFactory.createPoint(new Coordinate(lng, lat));
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String distanceLabel(double meters) {
        if (meters < 1000) return Math.round(meters) + " m";
        return String.format("%.1f km", meters / 1000);
    }

    @Transactional(readOnly = true)
    public Page<ServiceListing> search(String query, UUID categoryId, PriceType priceType,
                                       java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
                                       String city, String sortBy, Pageable pageable) {
        log.info("=== [ListingService.search] query: {}, category: {}, sortBy: {}, page: {}",
                query, categoryId, sortBy, pageable.getPageNumber());

        String effectiveSortBy = (sortBy == null || sortBy.isBlank()) ? "newest" : sortBy;
        return listingRepository.search(query, categoryId, priceType, minPrice, maxPrice, city, effectiveSortBy, pageable);
    }

    @Transactional(readOnly = true)
    public List<ServiceListing> getPopular() {
        log.info("=== [ListingService.getPopular] fetching top 10 listings by viewCount");
        return listingRepository.findTop10ByStatusAndIsVisibleOnMapTrueOrderByViewCountDesc(ListingStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<ServiceListing> getRecent() {
        log.info("=== [ListingService.getRecent] fetching top 10 recent verified listings");
        return listingRepository.findTop10ByStatusAndIsVerifiedTrueAndIsVisibleOnMapTrueOrderByCreatedAtDesc(ListingStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Page<ServiceListing> getByCategory(UUID categoryId, Pageable pageable) {
        log.info("=== [ListingService.getByCategory] category: {}, page: {}", categoryId, pageable.getPageNumber());
        return listingRepository.findByCategory_IdAndStatusAndIsVisibleOnMapTrue(categoryId, ListingStatus.ACTIVE, pageable);
    }
}
