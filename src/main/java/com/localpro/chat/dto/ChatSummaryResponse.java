package com.localpro.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSummaryResponse(
        UUID id,
        UUID otherPartyId,
        String otherPartyName,
        String otherPartyAvatarUrl,
        UUID listingId,
        String listingTitle,
        String lastMessage,
        Instant lastMessageAt,
        int unreadCount
) {}
