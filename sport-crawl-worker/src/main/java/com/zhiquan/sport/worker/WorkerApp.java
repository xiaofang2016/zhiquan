package com.zhiquan.sport.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.zhiquan.sport")
@ConfigurationPropertiesScan
@EnableScheduling
public class WorkerApp {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApp.class, args);
    }
}
