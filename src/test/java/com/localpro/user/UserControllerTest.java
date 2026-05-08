package com.localpro.user;

import com.localpro.AbstractIntegrationTest;
import com.localpro.user.dto.UpdateProfileRequest;
import com.localpro.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerTest extends AbstractIntegrationTest {

    @Test
    void getMe_returnsAuthenticatedUserProfile() {
        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("dev@localpro.com");
    }

    @Test
    void getMe_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void updateProfile_updatesName() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Updated Dev User", null, null, null, null, null);

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(request, authHeaders()),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Updated Dev User");
    }

    @Test
    void updateProfile_nameTooShort_returns400() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "X", null, null, null, null, null);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/users/me",
                HttpMethod.PUT,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getUserById_knownUser_returnsProfile() {
        UserResponse me = restTemplate.exchange(
                "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                UserResponse.class).getBody();

        ResponseEntity<UserResponse> response = restTemplate.exchange(
                "/api/users/" + me.id(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                UserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(me.id());
    }

    @Test
    void getUserById_unknownId_returns404() {
        UUID randomId = UUID.randomUUID();
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/users/" + randomId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateFcmToken_savesToken() {
        Map<String, String> body = Map.of("token", "test-fcm-123");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/users/me/fcm-token",
                HttpMethod.PUT,
                new HttpEntity<>(body, authHeaders()),
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
