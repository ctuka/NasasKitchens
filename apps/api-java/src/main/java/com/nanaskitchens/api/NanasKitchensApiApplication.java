package com.nanaskitchens.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Story 3.4: PendingPaymentSweeper releases abandoned pending orders
public class NanasKitchensApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NanasKitchensApiApplication.class, args);
    }
}
