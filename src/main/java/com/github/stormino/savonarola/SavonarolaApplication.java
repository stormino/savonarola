package com.github.stormino.savonarola;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SavonarolaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SavonarolaApplication.class, args);
    }
}
