package com.localpro.listing.dto;

import com.localpro.listing.ListingStatus;
import com.localpro.listing.PriceType;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateListingRequest(
        String title,
        String description,
        UUID categoryId,
        BigDecimal price,
        PriceType priceType,
        Double lat,
        Double lng,
        String address,
        String city,
        ListingStatus status
) {}
