package com.localpro.listing.dto;

import com.localpro.listing.PriceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ListingRequest(
        @NotBlank String title,
        String description,
        @NotNull UUID categoryId,
        BigDecimal price,
        PriceType priceType,
        @NotNull Double latitude,
        @NotNull Double longitude,
        String address,
        String city,
        List<String> photoUrls,
        List<String> customQuestions
) {}
