package com.localpro.booking.dto;

import com.localpro.booking.PaymentStatus;
import com.localpro.booking.PaymentType;

import java.math.BigDecimal;

public record BookingPaymentInfo(
        PaymentType type,
        PaymentStatus status,
        BigDecimal totalPrice
) {}
