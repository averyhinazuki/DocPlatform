package com.example.docplatform.tenant;

public class TenantContextHolder {
    private static final ThreadLocal<Long> CONTEXT = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) { CONTEXT.set(tenantId); }
    public static Long getTenantId() { return CONTEXT.get(); }
    public static void clear() { CONTEXT.remove(); }
}
