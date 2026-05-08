package com.localpro.listing;

import com.localpro.AbstractIntegrationTest;
import com.localpro.listing.dto.CategoryResponse;
import com.localpro.listing.dto.CreateListingRequest;
import com.localpro.listing.dto.ListingResponse;
import com.localpro.listing.dto.UpdateListingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListingControllerTest extends AbstractIntegrationTest {

    @Test
    void getCategories_returnsSeededCategories() {
        ResponseEntity<List<CategoryResponse>> response = restTemplate.exchange(
                "/api/categories",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void createListing_returnsCreated() {
        UUID categoryId = getFirstCategoryId();

        CreateListingRequest request = new CreateListingRequest(
                "Test Cleaning Service", "Professional cleaning",
                categoryId, BigDecimal.valueOf(50), PriceType.FROM, 50.45, 30.52, "Test St 1", "Kyiv");

        ResponseEntity<ListingResponse> response = restTemplate.exchange(
                "/api/listings",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                ListingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().title()).isEqualTo("Test Cleaning Service");
    }

    @Test
    void getListing_afterCreate_returnsListingWithIncrementedView() {
        UUID listingId = createListing("View Count Test");

        ResponseEntity<ListingResponse> first = restTemplate.exchange(
                "/api/listings/" + listingId,
                HttpMethod.GET, new HttpEntity<>(authHeaders()), ListingResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ListingResponse> second = restTemplate.exchange(
                "/api/listings/" + listingId,
                HttpMethod.GET, new HttpEntity<>(authHeaders()), ListingResponse.class);
        assertThat(second.getBody().viewCount()).isGreaterThan(first.getBody().viewCount());
    }

    @Test
    void getListing_notFound_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/" + UUID.randomUUID(),
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateListing_ownListing_updatesTitle() {
        UUID listingId = createListing("Original Title");
        UpdateListingRequest update = new UpdateListingRequest(
                "Updated Title", null, null, null, null, null, null, null, null, null);

        ResponseEntity<ListingResponse> response = restTemplate.exchange(
                "/api/listings/" + listingId,
                HttpMethod.PUT,
                new HttpEntity<>(update, authHeaders()),
                ListingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().title()).isEqualTo("Updated Title");
    }

    @Test
    void deleteListing_ownListing_returns204() {
        UUID listingId = createListing("To Delete");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/listings/" + listingId,
                HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void getMyListings_returnsOwnListings() {
        createListing("My Listing A");
        createListing("My Listing B");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/my",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    void findNearby_returnsPage() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/nearby?lat=50.45&lng=30.52&radiusKm=10",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    void createListing_withoutLocation_returns400() {
        UUID categoryId = getFirstCategoryId();
        CreateListingRequest request = new CreateListingRequest(
                "No Location", "Desc", categoryId, BigDecimal.valueOf(30),
                PriceType.FROM, null, null, "Street", "City");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createListing_withoutAuth_returns401() {
        UUID categoryId = getFirstCategoryId();
        CreateListingRequest request = new CreateListingRequest(
                "Unauthorized", null, categoryId, null, null, 50.0, 30.0, null, null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings",
                HttpMethod.POST,
                new HttpEntity<>(request),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    UUID createListing(String title) {
        UUID categoryId = getFirstCategoryId();
        CreateListingRequest request = new CreateListingRequest(
                title, "Description", categoryId, BigDecimal.valueOf(50),
                PriceType.FROM, 50.45, 30.52, "Street", "Kyiv");
        return restTemplate.exchange(
                "/api/listings",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                ListingResponse.class).getBody().id();
    }

    private UUID getFirstCategoryId() {
        return restTemplate.exchange(
                "/api/categories",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<List<CategoryResponse>>() {})
                .getBody().get(0).id();
    }
}
