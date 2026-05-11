package com.localpro.booking;

import com.localpro.booking.dto.CalendarLinks;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class MockCalendarService {

    private static final DateTimeFormatter GCAL_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    public CalendarLinks generateLinks(UUID listingId, LocalDateTime scheduledAt) {
        return generateLinks(CalendarType.IN_APP, listingId, scheduledAt);
    }

    public CalendarLinks generateLinks(CalendarType type, UUID listingId, LocalDateTime scheduledAt) {
        return switch (type) {
            case IN_APP -> new CalendarLinks(null, null);
            case CALENDLY -> new CalendarLinks(
                "https://calendly.com/localpro/" + listingId, null);
            case GOOGLE_CALENDAR -> new CalendarLinks(
                null, buildGoogleCalendarUrl(listingId, scheduledAt));
        };
    }

    private String buildGoogleCalendarUrl(UUID listingId, LocalDateTime scheduledAt) {
        String start = scheduledAt.format(GCAL_FORMAT);
        String end = scheduledAt.plusHours(1).format(GCAL_FORMAT);
        return "https://calendar.google.com/calendar/render?action=TEMPLATE" +
                "&text=LocalPro+Booking" +
                "&dates=" + start + "/" + end +
                "&details=Booking+for+service+listing+" + listingId;
    }
}
