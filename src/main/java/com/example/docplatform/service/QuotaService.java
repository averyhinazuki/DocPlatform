package com.example.docplatform.service;

import com.example.docplatform.exception.TenantQuotaExceededException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class QuotaService {

    private final RedissonClient redissonClient;

    public void acquire(Long tenantId, int limit) {
        RAtomicLong counter = redissonClient.getAtomicLong("tenant:" + tenantId + ":running");
        long current = counter.incrementAndGet();
        if (current == 1) {
            counter.expire(3600L, TimeUnit.SECONDS);
        }
        if (current > limit) {
            counter.decrementAndGet();
            throw new TenantQuotaExceededException(tenantId, limit);
        }
    }

    public void release(Long tenantId) {
        redissonClient.getAtomicLong("tenant:" + tenantId + ":running").decrementAndGet();
    }
}
