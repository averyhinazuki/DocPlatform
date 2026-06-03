package com.example.docplatform.service;

import com.example.docplatform.dto.tenant.TenantRequest;
import com.example.docplatform.dto.tenant.TenantResponse;
import com.example.docplatform.entity.Tenant;
import com.example.docplatform.mapper.TenantMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock TenantMapper tenantMapper;
    @InjectMocks TenantService tenantService;

    @Test
    void create_insertsAndReturnsTenant() {
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(tenantMapper.insert(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(1L);
            return 1;
        });

        TenantResponse resp = tenantService.create(new TenantRequest("Acme Corp", "acme", null));

        assertThat(resp.id()).isEqualTo(1L);
        assertThat(resp.slug()).isEqualTo("acme");
        assertThat(resp.plan()).isEqualTo("FREE");
        verify(tenantMapper).insert(any(Tenant.class));
    }

    @Test
    void create_throwsWhenSlugTaken() {
        when(tenantMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> tenantService.create(new TenantRequest("Dupe", "acme", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Slug already taken");
    }

    @Test
    void getLimit_returnsConcurrentJobLimit() {
        Tenant t = new Tenant();
        t.setId(1L);
        t.setConcurrentJobLimit(5);
        when(tenantMapper.selectById(1L)).thenReturn(t);

        assertThat(tenantService.getLimit(1L)).isEqualTo(5);
    }

    @Test
    void getLimit_throwsWhenTenantNotFound() {
        when(tenantMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> tenantService.getLimit(99L))
            .isInstanceOf(IllegalStateException.class);
    }
}
