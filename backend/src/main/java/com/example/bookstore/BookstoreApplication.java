package com.example.bookstore;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.bookstore.common.TimeConfig;

@SpringBootApplication
public class BookstoreApplication {

    public static void main(String[] args) {
        // The Alipay SDK parses its timestamps with the JVM default time zone; Alipay uses China time.
        TimeZone.setDefault(TimeZone.getTimeZone(TimeConfig.ZONE));
        SpringApplication.run(BookstoreApplication.class, args);
    }
}
