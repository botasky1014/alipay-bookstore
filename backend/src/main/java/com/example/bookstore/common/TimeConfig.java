package com.example.bookstore.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Alipay timestamps and order numbers use China Standard Time. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** Every "now" in the application comes from this bean so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
