package com.localpro.chat;

import com.localpro.AbstractIntegrationTest;
import com.localpro.chat.dto.ChatSummaryResponse;
import com.localpro.chat.dto.MessageResponse;
import com.localpro.chat.dto.SendContentRequest;
import com.localpro.chat.dto.StartChatRequest;
import com.localpro.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatControllerTest extends AbstractIntegrationTest {

    @Test
    void getChats_returnsListForAuthenticatedUser() {
        ResponseEntity<List<ChatSummaryResponse>> response = restTemplate.exchange(
                "/api/chats",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void getChats_unauthenticated_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/chats",
                HttpMethod.GET,
                new HttpEntity<>(null),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void startChat_withSelf_returnsChatSummary() {
        UUID devUserId = getDevUserId();
        StartChatRequest request = new StartChatRequest(devUserId, null);

        ResponseEntity<ChatSummaryResponse> response = restTemplate.exchange(
                "/api/chats",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                ChatSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isNotNull();
    }

    @Test
    void startChat_calledTwice_returnsSameChat() {
        UUID devUserId = getDevUserId();
        StartChatRequest request = new StartChatRequest(devUserId, null);

        ChatSummaryResponse first = restTemplate.exchange(
                "/api/chats", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                ChatSummaryResponse.class).getBody();

        ChatSummaryResponse second = restTemplate.exchange(
                "/api/chats", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                ChatSummaryResponse.class).getBody();

        assertThat(first.id()).isEqualTo(second.id());
    }

    @Test
    void sendMessage_toExistingChat_returnsCreated() {
        UUID chatId = startSelfChat();
        SendContentRequest request = new SendContentRequest("Hello from test!");

        ResponseEntity<MessageResponse> response = restTemplate.exchange(
                "/api/chats/" + chatId + "/messages",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                MessageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().content()).isEqualTo("Hello from test!");
    }

    @Test
    void getMessages_afterSending_returnsMessages() {
        UUID chatId = startSelfChat();
        sendMessage(chatId, "First message");
        sendMessage(chatId, "Second message");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/chats/" + chatId + "/messages",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("content");
    }

    @Test
    void sendMessage_toNonExistentChat_returns404() {
        SendContentRequest request = new SendContentRequest("Hello");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/chats/00000000-0000-0000-0000-000000000000/messages",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sendMessage_emptyContent_returns400() {
        UUID chatId = startSelfChat();
        SendContentRequest request = new SendContentRequest("");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/chats/" + chatId + "/messages",
                HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private UUID getDevUserId() {
        return restTemplate.exchange(
                "/api/users/me",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                UserResponse.class).getBody().id();
    }

    private UUID startSelfChat() {
        UUID devUserId = getDevUserId();
        return restTemplate.exchange(
                "/api/chats",
                HttpMethod.POST,
                new HttpEntity<>(new StartChatRequest(devUserId, null), authHeaders()),
                ChatSummaryResponse.class).getBody().id();
    }

    private void sendMessage(UUID chatId, String content) {
        restTemplate.exchange(
                "/api/chats/" + chatId + "/messages",
                HttpMethod.POST,
                new HttpEntity<>(new SendContentRequest(content), authHeaders()),
                MessageResponse.class);
    }
}
