package com.localpro.booking;

import java.math.BigDecimal;

public interface PaymentService {
    boolean processPayment(PaymentType type, BigDecimal amount);
}
