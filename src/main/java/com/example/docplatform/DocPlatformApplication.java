package com.example.docplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.example.docplatform.mapper")
@EnableScheduling
public class DocPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocPlatformApplication.class, args);
    }
}
