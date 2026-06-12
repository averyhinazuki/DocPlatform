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
        // Floored at 0: release is called once per terminal job outcome, but Kafka's
        // at-least-once delivery can replay a terminal message (e.g. the DLT handler) —
        // a duplicate release must not free a slot another job is holding via underflow.
        RAtomicLong counter = redissonClient.getAtomicLong("tenant:" + tenantId + ":running");
        long v;
        do {
            v = counter.get();
            if (v <= 0) return;
        } while (!counter.compareAndSet(v, v - 1));
    }
}
