package com.example.docplatform.report.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class DocumentStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public String upload(Long tenantId, String filename, byte[] content, String contentType) throws Exception {
        ensureBucketExists();
        String objectKey = "reports/" + tenantId + "/" + UUID.randomUUID() + "-" + filename;
        minioClient.putObject(PutObjectArgs.builder()
            .bucket(bucket).object(objectKey)
            .stream(new ByteArrayInputStream(content), content.length, -1)
            .contentType(contentType).build());
        return objectKey;
    }

    public String generatePresignedUrl(String objectKey) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
            .method(Method.GET).bucket(bucket).object(objectKey)
            .expiry(5, TimeUnit.MINUTES).build());
    }

    public void delete(String objectKey) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    private void ensureBucketExists() throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
