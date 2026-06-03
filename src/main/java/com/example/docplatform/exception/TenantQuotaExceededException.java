package com.example.docplatform.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TenantQuotaExceededException extends RuntimeException {
    public TenantQuotaExceededException(Long tenantId, int limit) {
        super("Concurrent report limit (" + limit + ") reached for tenant "
              + tenantId + ". Upgrade your plan.");
    }
}
