package com.example.incident;

public final class TenantOnboardingTest {
    public static void main(String[] args) {
        TenantOnboarding onboarding = new TenantOnboarding();
        TenantOnboarding.Tenant tenant = new TenantOnboarding.Tenant("tenant-acme", "risk@example.com");
        TenantOnboarding.LifecycleEvent event = new TenantOnboarding.LifecycleEvent(tenant, true, "admin-export");
        if (onboarding.decide(event) != TenantOnboarding.Decision.ROTATE_AND_AUDIT) {
            throw new AssertionError("leaked key must trigger rotation and audit");
        }
        System.out.println("ROTATE_AND_AUDIT");
    }
}
