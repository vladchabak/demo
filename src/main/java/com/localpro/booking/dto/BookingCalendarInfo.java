package com.localpro.booking.dto;

import com.localpro.booking.CalendarType;

public record BookingCalendarInfo(
        CalendarType type,
        String calendlyLink,
        String googleCalendarLink
) {}
