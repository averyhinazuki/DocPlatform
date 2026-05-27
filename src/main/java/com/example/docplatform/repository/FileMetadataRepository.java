package com.example.docplatform.repository;

import com.example.docplatform.document.FileMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {
    Optional<FileMetadata> findByIdAndTenantId(String id, Long tenantId);
}
