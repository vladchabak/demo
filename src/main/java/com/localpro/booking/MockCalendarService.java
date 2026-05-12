package com.localpro.booking;

import com.localpro.booking.dto.CalendarLinks;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class MockCalendarService implements CalendarService {

    private static final DateTimeFormatter GCAL_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);

    @Override
    public CalendarLinks generateLinks(CalendarType type, UUID listingId, Instant scheduledAt) {
        return switch (type) {
            case IN_APP -> new CalendarLinks(null, null);
            case CALENDLY -> new CalendarLinks(
                "https://calendly.com/localpro/" + listingId, null);
            case GOOGLE_CALENDAR -> new CalendarLinks(
                null, buildGoogleCalendarUrl(listingId, scheduledAt));
        };
    }

    private String buildGoogleCalendarUrl(UUID listingId, Instant scheduledAt) {
        String start = GCAL_FORMAT.format(scheduledAt);
        String end = GCAL_FORMAT.format(scheduledAt.plus(Duration.ofHours(1)));
        return "https://calendar.google.com/calendar/render?action=TEMPLATE" +
                "&text=LocalPro+Booking" +
                "&dates=" + start + "/" + end +
                "&details=Booking+for+service+listing+" + listingId;
    }
}
