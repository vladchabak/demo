package com.localpro.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendContentRequest(
        @NotBlank @Size(max = 2000) String content
) {}
