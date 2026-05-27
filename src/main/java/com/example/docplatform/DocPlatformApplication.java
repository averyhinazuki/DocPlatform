package com.example.docplatform;

import com.example.docplatform.entity.Tenant;
import com.example.docplatform.mapper.TenantMapper;
import org.jetbrains.annotations.TestOnly;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
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
