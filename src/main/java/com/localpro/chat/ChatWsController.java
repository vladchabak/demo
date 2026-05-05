package com.localpro.chat;

import com.localpro.chat.dto.SendMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        if (principal == null) {
            log.warn("WebSocket message received without principal - ignoring");
            return;
        }
        UUID senderId = UUID.fromString(principal.getName());
        chatService.sendMessage(request.chatId(), senderId, request.content());
    }
}
