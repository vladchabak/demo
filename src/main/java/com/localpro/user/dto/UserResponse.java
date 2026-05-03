package com.localpro.user.dto;

import com.localpro.user.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String firebaseUid,
        String email,
        String name,
        String avatarUrl,
        UserRole role,
        String bio,
        String phone,
        BigDecimal rating,
        Integer reviewCount,
        Boolean isActive,
        Instant createdAt
) {}
