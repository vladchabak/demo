package com.localpro.listing;

import com.localpro.auth.CurrentUser;
import com.localpro.listing.dto.*;
import com.localpro.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final CategoryService categoryService;
    private final ListingMapper listingMapper;

    @PostMapping("/listings")
    public ResponseEntity<ListingResponse> create(@CurrentUser User user,
                                                  @Valid @RequestBody CreateListingRequest request) {
        ServiceListing listing = listingService.create(user, request);
        return ResponseEntity
                .created(URI.create("/api/listings/" + listing.getId()))
                .body(listingMapper.toResponse(listing));
    }

    @PutMapping("/listings/{id}")
    public ResponseEntity<ListingResponse> update(@CurrentUser User user,
                                                  @PathVariable UUID id,
                                                  @RequestBody UpdateListingRequest request) {
        ServiceListing listing = listingService.update(user.getId(), id, request);
        return ResponseEntity.ok(listingMapper.toResponse(listing));
    }

    @DeleteMapping("/listings/{id}")
    public ResponseEntity<Void> delete(@CurrentUser User user, @PathVariable UUID id) {
        listingService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/listings/{id}")
    public ResponseEntity<ListingResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(listingMapper.toResponse(listingService.getById(id)));
    }

    @GetMapping("/listings/my")
    public ResponseEntity<Page<ListingResponse>> getMyListings(
            @CurrentUser User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(listingService.getByProvider(user.getId(), pageable)
                .map(listingMapper::toResponse));
    }

    @GetMapping("/listings/nearby")
    public ResponseEntity<Page<NearbyListingResponse>> findNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radiusKm,
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(listingService.findNearby(lat, lng, radiusKm, categoryId, page, size));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(categoryService.getTopLevelCategories());
    }
}
