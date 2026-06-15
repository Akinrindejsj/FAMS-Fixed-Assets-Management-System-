package com.example.fams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class FamsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamsApplication.class, args);
    }

}
