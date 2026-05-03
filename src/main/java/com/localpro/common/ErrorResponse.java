package com.localpro.common;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(String code, String message, List<String> fieldErrors, Instant timestamp) {

    public ErrorResponse(String code, String message) {
        this(code, message, List.of(), Instant.now());
    }

    public ErrorResponse(String code, String message, List<String> fieldErrors) {
        this(code, message, fieldErrors, Instant.now());
    }
}
