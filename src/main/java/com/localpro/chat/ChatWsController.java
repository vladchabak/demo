package com.localpro.chat;

import com.localpro.chat.dto.MessageResponse;
import com.localpro.chat.dto.SendMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;
    private final MessageMapper messageMapper;

    @MessageMapping("/chat.send")
    @SendToUser("/queue/messages")
    public MessageResponse sendMessage(@Payload SendMessageRequest request, Principal principal) {
        UUID senderId = UUID.fromString(principal.getName());
        Message msg = chatService.sendMessage(request.chatId(), senderId, request.content());
        return messageMapper.toResponse(msg);
    }
}
