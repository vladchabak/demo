package com.localpro.listing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdatePhotoOrderRequest(
        @NotNull List<UUID> photoIds
) {}
