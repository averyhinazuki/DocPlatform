package com.example.docplatform.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void quotaExceeded_mapsTo429WithMessage() {
        // Regression guard: the catch-all Exception handler outranks @ResponseStatus
        // on the exception class, so quota rejections need their own handler method.
        // Found by the JMeter load test (rejections returned 500).
        ResponseEntity<Map<String, String>> res =
                handler.quotaExceeded(new TenantQuotaExceededException(1L, 3));

        assertThat(res.getStatusCode().value()).isEqualTo(429);
        assertThat(res.getBody().get("error")).contains("Concurrent report limit");
    }

    @Test
    void generic_mapsTo500WithoutLeakingDetails() {
        ResponseEntity<Map<String, String>> res =
                handler.generic(new RuntimeException("secret internal detail"));

        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().get("error")).isEqualTo("Internal server error");
    }
}
