package com.localpro.booking.dto;

import java.util.UUID;

public record BookingCustomerInfo(
        UUID id,
        String name
) {}
