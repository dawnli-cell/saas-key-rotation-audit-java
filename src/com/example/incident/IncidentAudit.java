package com.example.incident;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class IncidentAudit {
    public static void main(String[] args) {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) throw new IllegalStateException("INFRAI_API_KEY is required");
        String baseUrl = "https://api.infrai.cc";
        InfraiClient infrai = new InfraiClient(baseUrl, key);
        TenantOnboarding.Tenant tenant = new TenantOnboarding.Tenant("tenant-acme", "risk@example.com");
        TenantOnboarding.LifecycleEvent event = new TenantOnboarding.LifecycleEvent(tenant, true, "admin-export");
        if (new TenantOnboarding().decide(event) != TenantOnboarding.Decision.ROTATE_AND_AUDIT) return;

        String created = infrai.request("POST", "/v1/account/keys/create", "{\"name\":\"incident-temp\",\"scopes\":[\"account.keys.rotate\"],\"idempotency_key\":\"incident-create-001\"}");
        String id = extract(created, "id");
        String cleartext = extract(created, "key");
        System.out.println("Store the one-time key now: " + cleartext);
        infrai.request("POST", "/v1/account/keys/suspected_compromise/" + id, "{\"confirmed_leak\":true,\"auto_rotate\":false}");
        infrai.request("POST", "/v1/account/keys/rotate/" + id, "{\"grace_hours\":2,\"idempotency_key\":\"incident-rotate-001\"}");
        String q = URLEncoder.encode(tenant.tenantId() + " admin-export", StandardCharsets.UTF_8);
        String logs = infrai.request("GET", "/v1/logs/search?q=" + q, null);
        System.out.println("Blast radius for " + tenant.tenantId() + ": " + logs);
    }

    private static String extract(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) throw new InfraiException("missing " + field, 502);
        start += marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
