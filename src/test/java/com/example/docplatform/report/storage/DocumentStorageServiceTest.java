package com.example.docplatform.report.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf("isMinioReachable")
class DocumentStorageServiceTest {

    private static final String ENDPOINT = System.getProperty("minio.endpoint", "http://localhost:9000");
    private static final String ACCESS_KEY = System.getProperty("minio.access-key", "minioadmin");
    private static final String SECRET_KEY = System.getProperty("minio.secret-key", "minioadmin");
    private static final String BUCKET = "reports";

    private static MinioClient client;
    private DocumentStorageService storageService;

    static boolean isMinioReachable() {
        try {
            URL url = new URL(ENDPOINT + "/minio/health/live");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void initClient() throws Exception {
        client = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESS_KEY, SECRET_KEY).build();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }
    }

    @BeforeEach
    void setUp() {
        storageService = new DocumentStorageService(client, BUCKET);
    }

    @Test
    void uploadAndPresignedUrl() throws Exception {
        byte[] content = "hello".getBytes();
        String key = storageService.upload(1L, "test.txt", content, "text/plain");
        assertThat(key).startsWith("reports/1/").endsWith("-test.txt");
        String url = storageService.generatePresignedUrl(key);
        assertThat(url).isNotBlank().contains(BUCKET);
    }
}
