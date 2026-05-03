package com.localpro.listing.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        Integer rating,
        String comment,
        UUID clientId,
        String clientName,
        String clientAvatarUrl,
        Instant createdAt
) {}
