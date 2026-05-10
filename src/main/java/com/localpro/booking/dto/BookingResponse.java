package com.localpro.booking.dto;

import com.localpro.booking.BookingStatus;
import com.localpro.booking.CalendarType;
import com.localpro.booking.PaymentStatus;
import com.localpro.booking.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID listingId,
        String listingTitle,
        UUID customerId,
        String customerName,
        UUID providerId,
        String providerName,
        BookingStatus status,
        LocalDateTime scheduledAt,
        PaymentType paymentType,
        PaymentStatus paymentStatus,
        BigDecimal totalPrice,
        String notes,
        CalendarType calendarType,
        String calendarEventId,
        String calendlyLink,
        String googleCalendarLink,
        Instant createdAt,
        Instant updatedAt
) {}
