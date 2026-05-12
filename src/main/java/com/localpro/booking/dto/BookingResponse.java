package com.localpro.booking.dto;

import com.localpro.booking.BookingStatus;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        BookingStatus status,
        Instant scheduledAt,
        Instant createdAt,
        Instant updatedAt,
        BookingListingInfo listing,
        BookingProviderInfo provider,
        BookingCustomerInfo customer,
        BookingPaymentInfo payment,
        BookingCalendarInfo calendar,
        String notes,
        String cancellationReason,
        BookingActions actions
) {}
