package com.example.incident;

public final class TenantOnboarding {
    public record Tenant(String tenantId, String accountOwner) {}
    public record LifecycleEvent(Tenant tenant, boolean leakedKey, String operation) {}

    public enum Decision { ROTATE_AND_AUDIT, OBSERVE }

    public Decision decide(LifecycleEvent event) {
        if (event.leakedKey() && event.tenant().tenantId() != null && !event.tenant().tenantId().isBlank()) {
            return Decision.ROTATE_AND_AUDIT;
        }
        return Decision.OBSERVE;
    }
}
