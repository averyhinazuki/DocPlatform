package com.example.docplatform.service;

import com.example.docplatform.exception.TenantQuotaExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock RedissonClient redissonClient;
    @Mock RAtomicLong counter;
    QuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new QuotaService(redissonClient);
        when(redissonClient.getAtomicLong(anyString())).thenReturn(counter);
    }

    @Test
    void acquire_allowsWhenUnderLimit() {
        when(counter.incrementAndGet()).thenReturn(2L);

        assertThatCode(() -> quotaService.acquire(1L, 3)).doesNotThrowAnyException();
    }

    @Test
    void acquire_allowsAtExactLimit() {
        when(counter.incrementAndGet()).thenReturn(3L);

        assertThatCode(() -> quotaService.acquire(1L, 3)).doesNotThrowAnyException();
    }

    @Test
    void acquire_throwsAndDecrementsWhenOverLimit() {
        when(counter.incrementAndGet()).thenReturn(4L);

        assertThatThrownBy(() -> quotaService.acquire(1L, 3))
            .isInstanceOf(TenantQuotaExceededException.class);
        verify(counter).decrementAndGet();
    }

    @Test
    void acquire_setsTtlOnFirstJob() {
        when(counter.incrementAndGet()).thenReturn(1L);

        quotaService.acquire(1L, 3);

        verify(counter).expire(3600L, TimeUnit.SECONDS);
    }

    @Test
    void acquire_doesNotResetTtlOnSubsequentJobs() {
        when(counter.incrementAndGet()).thenReturn(2L);

        quotaService.acquire(1L, 3);

        verify(counter, never()).expire(anyLong(), any());
    }

    @Test
    void release_decrementsCounterViaCas() {
        when(counter.get()).thenReturn(2L);
        when(counter.compareAndSet(2L, 1L)).thenReturn(true);

        quotaService.release(1L);

        verify(counter).compareAndSet(2L, 1L);
    }

    @Test
    void release_noOpWhenCounterAlreadyZero() {
        when(counter.get()).thenReturn(0L);

        quotaService.release(1L);

        verify(counter, never()).compareAndSet(anyLong(), anyLong());
    }
}
