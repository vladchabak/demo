package com.localpro.booking.dto;

import com.localpro.listing.PriceType;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingListingInfo(
        UUID id,
        String title,
        String address,
        String city,
        BigDecimal price,
        PriceType priceType,
        String photoUrl
) {}
