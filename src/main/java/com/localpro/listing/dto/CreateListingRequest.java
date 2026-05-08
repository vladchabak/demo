package com.localpro.listing.dto;

import com.localpro.listing.PriceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateListingRequest(
        @NotBlank String title,
        String description,
        @NotNull UUID categoryId,
        BigDecimal price,
        PriceType priceType,
        @NotNull Double lat,
        @NotNull Double lng,
        String address,
        String city
) {}
