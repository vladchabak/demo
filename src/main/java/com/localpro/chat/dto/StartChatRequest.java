package com.localpro.chat.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartChatRequest(
        @NotNull UUID providerId,
        UUID listingId
) {}
