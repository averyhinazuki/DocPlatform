package com.example.docplatform.repository;

import com.example.docplatform.document.ReportTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ReportTemplateRepository extends MongoRepository<ReportTemplate, String> {
    List<ReportTemplate> findByTenantId(Long tenantId);
    Optional<ReportTemplate> findByIdAndTenantId(String id, Long tenantId);
}
