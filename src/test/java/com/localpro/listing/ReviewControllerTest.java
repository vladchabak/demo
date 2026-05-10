package com.localpro.listing;

import com.localpro.AbstractIntegrationTest;
import com.localpro.listing.dto.CategoryResponse;
import com.localpro.listing.dto.ListingRequest;
import com.localpro.listing.dto.CreateReviewRequest;
import com.localpro.listing.dto.ListingResponse;
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

class ReviewControllerTest extends AbstractIntegrationTest {

    @Test
    void getReviews_returnsEmptyPageByDefault() {
        UUID listingId = createListing();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/" + listingId + "/reviews",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    void getReviews_unknownListing_returnsEmptyPage() {
        UUID unknownId = UUID.randomUUID();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/" + unknownId + "/reviews",
                HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void createReview_selfReview_returns400() {
        UUID listingId = createListing();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/" + listingId + "/reviews",
                HttpMethod.POST,
                new HttpEntity<>(new CreateReviewRequest(5, "Great"), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").toString()).contains("own listing");
    }

    @Test
    void createReview_ratingTooHigh_returns400() {
        UUID listingId = createListing();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/" + listingId + "/reviews",
                HttpMethod.POST,
                new HttpEntity<>(new CreateReviewRequest(6, "Too good"), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createReview_ratingTooLow_returns400() {
        UUID listingId = createListing();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/" + listingId + "/reviews",
                HttpMethod.POST,
                new HttpEntity<>(new CreateReviewRequest(0, "Bad"), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createReview_nullRating_returns400() {
        UUID listingId = createListing();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/" + listingId + "/reviews",
                HttpMethod.POST,
                new HttpEntity<>(new CreateReviewRequest(null, "No rating"), authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createReview_unauthenticated_returns401() {
        UUID listingId = createListing();

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/listings/" + listingId + "/reviews",
                HttpMethod.POST,
                new HttpEntity<>(new CreateReviewRequest(5, "Good")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private UUID createListing() {
        ResponseEntity<List<CategoryResponse>> categories = restTemplate.exchange(
                "/api/categories", HttpMethod.GET,
                new HttpEntity<>(authHeaders()), new ParameterizedTypeReference<>() {});
        UUID categoryId = categories.getBody().get(0).id();

        ListingRequest request = new ListingRequest(
                "Review Test Listing", "For review tests",
                categoryId, BigDecimal.valueOf(40), PriceType.PER_SERVICE, 50.45, 30.52, "Street", "Kyiv", null, null);

        return restTemplate.exchange(
                "/api/listings", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                ListingResponse.class).getBody().id();
    }
}
