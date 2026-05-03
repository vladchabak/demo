package com.localpro.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID chatId,
        UUID senderId,
        String senderName,
        String senderAvatarUrl,
        String content,
        Boolean isRead,
        Instant createdAt
) {}
