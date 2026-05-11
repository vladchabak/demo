package com.localpro.booking;

import com.localpro.booking.dto.CalendarLinks;

import java.time.Instant;
import java.util.UUID;

public interface CalendarService {
    CalendarLinks generateLinks(CalendarType type, UUID listingId, Instant scheduledAt);
}
