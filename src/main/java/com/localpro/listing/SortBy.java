package com.localpro.listing;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SortBy {
    NEWEST, PRICE_ASC, PRICE_DESC, RATING, POPULAR, MOST_USED;

    @JsonCreator
    public static SortBy fromValue(String value) {
        return valueOf(value.toUpperCase());
    }
}
