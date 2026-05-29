package com.example.docplatform.service;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.dto.report.ReportRequest;
import com.example.docplatform.enums.ReportStatus;
import com.example.docplatform.kafka.event.ReportRequestedEvent;
import com.example.docplatform.kafka.producer.ReportJobProducer;
import com.example.docplatform.repository.GeneratedDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final RedissonClient redissonClient;
    private final ReportJobProducer producer;
    private final GeneratedDocumentRepository documentRepository;

    public String requestReport(Long tenantId, ReportRequest req, String triggeredBy) {
        String lockKey = req.scheduleId() != null
            ? "report:" + tenantId + ":" + req.scheduleId()
            : "report:assignment:" + tenantId + ":" + req.assignmentId();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Report generation already in progress for this schedule");
            }

            if (req.scheduleId() != null) {
                boolean alreadyQueued = documentRepository.existsByScheduleIdAndStatusIn(
                    req.scheduleId(), List.of(ReportStatus.PENDING, ReportStatus.IN_PROGRESS));
                if (alreadyQueued) {
                    throw new IllegalStateException("Report already queued");
                }
            }

            GeneratedDocument doc = new GeneratedDocument();
            doc.setTenantId(tenantId);
            doc.setScheduleId(req.scheduleId());
            doc.setFileFormat(req.format());
            doc.setStatus(ReportStatus.PENDING);
            doc.setGeneratedAt(LocalDateTime.now());
            documentRepository.save(doc);

            producer.publishRequest(new ReportRequestedEvent(
                doc.getId(), tenantId, req.scheduleId(),
                req.reportType(), req.format().name(),
                req.templateId(), req.params(), req.recipients(), triggeredBy, req.note()
            ));

            return doc.getId();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted acquiring lock", e);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
