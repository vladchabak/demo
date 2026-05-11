package com.localpro.booking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@Profile("!prod")
public class MockPaymentService implements PaymentService {

    @Override
    public boolean processPayment(PaymentType type, BigDecimal amount) {
        log.info("MOCK PAYMENT processed: type={} amount={}", type, amount);
        return true;
    }
}
