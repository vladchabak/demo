package com.localpro.user.dto;

import com.localpro.user.UserRole;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 2, max = 255) String name,
        @Size(max = 1000) String bio,
        @Size(max = 30) String phone,
        String avatarUrl,
        UserRole role,
        String fcmToken
) {}
