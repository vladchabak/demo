package com.localpro.common;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest createRequest() {
        return new MockHttpServletRequest("GET", "/api/test");
    }

    @Test
    void entityNotFound_returns404WithCode() {
        MockHttpServletRequest request = createRequest();
        ResponseEntity<Map<String, Object>> response =
                handler.handleNotFound(new EntityNotFoundException("Listing not found: abc"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().get("code")).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().get("message")).asString().contains("Listing not found");
    }

    @Test
    void accessDenied_returns403WithGenericMessage() {
        MockHttpServletRequest request = createRequest();
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccessDenied(new AccessDeniedException("nope"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().get("code")).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void illegalArgument_returns400() {
        MockHttpServletRequest request = createRequest();
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Cannot review own listing"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().get("code")).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().get("message")).isEqualTo("Cannot review own listing");
    }

    @Test
    void unexpectedException_returns500WithGenericMessage() {
        MockHttpServletRequest request = createRequest();
        ResponseEntity<Map<String, Object>> response =
                handler.handleGeneral(new RuntimeException("DB exploded"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().get("code")).isEqualTo("INTERNAL_ERROR");
    }
}
