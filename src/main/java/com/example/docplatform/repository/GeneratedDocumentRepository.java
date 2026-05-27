package com.example.docplatform.repository;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.enums.ReportStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GeneratedDocumentRepository extends MongoRepository<GeneratedDocument, String> {
    List<GeneratedDocument> findByTenantIdOrderByGeneratedAtDesc(Long tenantId);
    boolean existsByScheduleIdAndStatusIn(Long scheduleId, List<ReportStatus> statuses);
}
