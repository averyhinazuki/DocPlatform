package com.example.docplatform.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic reportRequested() {
        return TopicBuilder.name("report.requested").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic reportCompleted() {
        return TopicBuilder.name("report.completed").partitions(3).replicas(1).build();
    }
}
