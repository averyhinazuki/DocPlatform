package com.example.docplatform.service;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.enums.FileFormat;
import com.example.docplatform.kafka.event.ReportRequestedEvent;
import com.example.docplatform.kafka.producer.ReportJobProducer;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock RedissonClient redissonClient;
    @Mock RLock lock;
    @Mock ReportJobProducer producer;
    @Mock GeneratedDocumentRepository documentRepository;
    @InjectMocks ReportService reportService;

    @Test
    void requestReport_acquiresLockAndPublishes() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(documentRepository.existsByScheduleIdAndStatusIn(any(), any())).thenReturn(false);
        when(documentRepository.save(any())).thenAnswer(inv -> {
            GeneratedDocument d = inv.getArgument(0);
            d.setId("doc-1");
            return d;
        });

        String id = reportService.requestReport(1L,
            new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of("a@b.com"), null, null), "alice");

        assertThat(id).isEqualTo("doc-1");
        verify(producer).publishRequest(any(ReportRequestedEvent.class));
        verify(lock).unlock();
    }

    @Test
    void requestReport_throwsWhenLockNotAcquired() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> reportService.requestReport(1L,
            new ReportRequest(10L, "SALES", FileFormat.PDF, "tmpl-1", Map.of(), List.of(), null, null), "alice"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("in progress");
    }
}
