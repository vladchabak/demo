package com.localpro.listing.dto;

import com.localpro.listing.ListingStatus;
import com.localpro.listing.PriceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ListingResponse(
        UUID id,
        String title,
        String description,
        UUID categoryId,
        String categoryName,
        UUID providerId,
        String providerName,
        String providerAvatarUrl,
        BigDecimal providerRating,
        BigDecimal price,
        PriceType priceType,
        String address,
        String city,
        ListingStatus status,
        Integer viewCount,
        BigDecimal rating,
        Integer reviewCount,
        List<String> photoUrls,
        Instant createdAt,
        boolean isVerified,
        LocalDateTime verifiedAt,
        List<String> customQuestions,
        boolean isVisibleOnMap
) {}
