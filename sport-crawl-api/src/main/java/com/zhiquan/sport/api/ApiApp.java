package com.zhiquan.sport.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.zhiquan.sport")
public class ApiApp {

    public static void main(String[] args) {
        SpringApplication.run(ApiApp.class, args);
    }
}
