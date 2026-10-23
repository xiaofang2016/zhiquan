package com.zhiquan.sport.db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.zhiquan.sport")
@ConfigurationPropertiesScan
@EnableScheduling
public class DbApp {

    public static void main(String[] args) {
        SpringApplication.run(DbApp.class, args);
    }
}
