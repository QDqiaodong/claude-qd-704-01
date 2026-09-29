package com.fire.protect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FireProtectApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireProtectApplication.class, args);
    }
}
