package com.localpro.booking.dto;

import com.localpro.booking.CalendarType;
import com.localpro.booking.PaymentType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID listingId,
        @NotNull LocalDateTime scheduledAt,
        @NotNull PaymentType paymentType,
        CalendarType calendarType,
        BigDecimal totalPrice,
        String notes
) {}
