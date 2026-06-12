package com.example.docplatform.repository;

import com.example.docplatform.document.GeneratedDocument;
import com.example.docplatform.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GeneratedDocumentRepository extends MongoRepository<GeneratedDocument, String> {
    Page<GeneratedDocument> findByTenantIdOrderByGeneratedAtDesc(Long tenantId, Pageable pageable);
    Page<GeneratedDocument> findByTenantIdAndUserIdOrderByGeneratedAtDesc(Long tenantId, Long userId, Pageable pageable);
    boolean existsByScheduleIdAndStatusIn(Long scheduleId, List<ReportStatus> statuses);
}
