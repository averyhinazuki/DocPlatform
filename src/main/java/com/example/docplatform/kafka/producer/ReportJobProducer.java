package com.example.docplatform.kafka.producer;

import com.example.docplatform.kafka.event.ReportCompletedEvent;
import com.example.docplatform.kafka.event.ReportRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReportJobProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRequest(ReportRequestedEvent event) {
        kafkaTemplate.send("report.requested", event.documentId(), event);
    }

    public void publishCompleted(ReportCompletedEvent event) {
        kafkaTemplate.send("report.completed", event.documentId(), event);
    }
}
