package com.nemo.nemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NemoApplication {

    // Spring Boot 애플리케이션 진입점
    public static void main(String[] args) {
        SpringApplication.run(NemoApplication.class, args);
    }
}
