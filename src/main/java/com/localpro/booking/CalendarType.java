package com.localpro.booking;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CalendarType {
    IN_APP,
    CALENDLY,
    GOOGLE_CALENDAR;

    @JsonCreator
    public static CalendarType fromString(String value) {
        if (value == null) return IN_APP;
        return CalendarType.valueOf(value.toUpperCase().replace("-", "_"));
    }
}
