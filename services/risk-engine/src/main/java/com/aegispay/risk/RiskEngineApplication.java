package com.aegispay.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RiskEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskEngineApplication.class, args);
    }
}
