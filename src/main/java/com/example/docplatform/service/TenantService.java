package com.example.docplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.docplatform.dto.tenant.TenantRequest;
import com.example.docplatform.dto.tenant.TenantResponse;
import com.example.docplatform.entity.Tenant;
import com.example.docplatform.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;

    public TenantResponse create(TenantRequest req) {
        Long count = tenantMapper.selectCount(
            new LambdaQueryWrapper<Tenant>().eq(Tenant::getSlug, req.slug()));
        if (count > 0) throw new IllegalArgumentException("Slug already taken: " + req.slug());

        Tenant t = new Tenant();
        t.setName(req.name());
        t.setSlug(req.slug());
        t.setPlan(req.plan() != null ? req.plan() : "FREE");
        t.setCreatedAt(LocalDateTime.now());
        tenantMapper.insert(t);
        return toResponse(t);
    }

    public List<TenantResponse> list() {
        return tenantMapper.selectList(null).stream().map(this::toResponse).toList();
    }

    public int getLimit(Long tenantId) {
        Tenant t = tenantMapper.selectById(tenantId);
        if (t == null) throw new IllegalStateException("Tenant not found: " + tenantId);
        return t.getConcurrentJobLimit();
    }

    private TenantResponse toResponse(Tenant t) {
        return new TenantResponse(t.getId(), t.getName(), t.getSlug(), t.getPlan(), t.getCreatedAt());
    }
}
