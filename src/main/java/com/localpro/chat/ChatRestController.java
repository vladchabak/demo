package com.localpro.chat;

import com.localpro.auth.CurrentUser;
import com.localpro.chat.dto.ChatSummaryResponse;
import com.localpro.chat.dto.MessageResponse;
import com.localpro.chat.dto.SendContentRequest;
import com.localpro.chat.dto.StartChatRequest;
import com.localpro.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatSummaryResponse> startChat(@CurrentUser User user,
                                                         @Valid @RequestBody StartChatRequest request) {
        return ResponseEntity.ok(chatService.getOrCreateChat(
                user.getId(), request.providerId(), request.listingId()));
    }

    @GetMapping
    public ResponseEntity<List<ChatSummaryResponse>> getChats(@CurrentUser User user) {
        return ResponseEntity.ok(chatService.getChats(user.getId()));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @CurrentUser User user,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(
                chatService.getMessages(id, user.getId(), PageRequest.of(page, size)));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @CurrentUser User user,
            @PathVariable UUID id,
            @Valid @RequestBody SendContentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.sendMessage(id, user.getId(), request.content()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@CurrentUser User user, @PathVariable UUID id) {
        chatService.markRead(id, user.getId());
        return ResponseEntity.ok().build();
    }
}
