package com.example.bookstore.order;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.example.bookstore.common.TimeConfig;

/** Order number = yyyyMMddHHmmss (China time) + 6 random digits; also used as Alipay out_trade_no. */
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public OrderNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public String next() {
        String time = LocalDateTime.ofInstant(clock.instant(), TimeConfig.ZONE).format(FORMAT);
        return time + String.format("%06d", random.nextInt(1_000_000));
    }
}
