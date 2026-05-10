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
        String calendlyLink = "https://calendly.com/localpro/" + listingId;

        String start = scheduledAt.format(GCAL_FORMAT);
        String end = scheduledAt.plusHours(1).format(GCAL_FORMAT);
        String googleCalendarLink = "https://calendar.google.com/calendar/render?action=TEMPLATE" +
                "&text=LocalPro+Booking" +
                "&dates=" + start + "/" + end +
                "&details=Booking+for+service+listing+" + listingId;

        return new CalendarLinks(calendlyLink, googleCalendarLink);
    }
}
