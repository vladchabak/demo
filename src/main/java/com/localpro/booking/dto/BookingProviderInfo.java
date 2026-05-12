package com.localpro.booking.dto;

import java.util.UUID;

public record BookingProviderInfo(
        UUID id,
        String name,
        String avatarUrl,
        String phone
) {}
