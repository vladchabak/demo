package com.localpro.booking.dto;

public record BookingActions(
        boolean canCancel,
        boolean canConfirm
) {}
