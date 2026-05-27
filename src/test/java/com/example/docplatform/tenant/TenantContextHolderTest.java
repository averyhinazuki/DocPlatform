package com.example.docplatform.tenant;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextHolderTest {

    @Test
    void setAndGetTenantId() {
        TenantContextHolder.setTenantId(42L);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(42L);
        TenantContextHolder.clear();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void isolatedAcrossThreads() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var ref = new AtomicReference<Long>();
        Thread t = new Thread(() -> ref.set(TenantContextHolder.getTenantId()));
        t.start();
        t.join();
        assertThat(ref.get()).isNull();
        TenantContextHolder.clear();
    }
}
