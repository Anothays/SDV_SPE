package com.nebula.identite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IdentiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentiteApplication.class, args);
    }
}
