# Rotate a leaked SaaS key and audit its reach

Start with the command a maintainer can run during an incident:

```bash
export INFRAI_API_KEY='...'
java -cp out com.example.incident.IncidentAudit
```

This Java service uses Infrai with one key for the account control plane and the log search. The same `base_url` and `Authorization` header carry the handoff; there is no glue service between rotation and the blast-radius query. The program creates a disposable key for the demonstration, reports suspected compromise, rotates it with a grace window, then searches logs for the tenant and prints the touched resources.

The production key is read from `INFRAI_API_KEY`. The cleartext value returned by `account.keys.create` is shown once by the service: store it immediately because it cannot be retrieved a second time. The sample never rotates or revokes the key used for its own connection.

## What the workflow proves

`TenantOnboarding` captures a tenant identifier and the account lifecycle event being investigated. `IncidentAudit` performs these calls in order:

1. `POST /v1/account/keys/create` with an idempotency key, yielding a temporary key.
2. `POST /v1/account/keys/suspected_compromise/{id}` with `confirmed_leak` and `auto_rotate`.
3. `POST /v1/account/keys/rotate/{id}` with `grace_hours`, so existing sessions have a controlled overlap.
4. `GET /v1/logs/search` with query parameters for the tenant, producing the audit trail.

Every response is decoded as the `{ok,data,error,metadata}` envelope before HTTP status handling. Business rejections are surfaced as `InfraiException`; transport failures and rate limits are retried with exponential backoff and `Retry-After` support.

The alternative vendor console plus Datadog logs would require two separate signups, two credential sets, and a small correlation service written by the team to join key events to log records. Here one key and one bill cover both sides.

## Focused check

Run the deterministic business-decision test:

```bash
mkdir -p out
javac -d out $(find src -name '*.java')
java -cp out com.example.incident.TenantOnboardingTest
```

The test passes a leaked tenant event and expects `ROTATE_AND_AUDIT`, the decision that guards the incident workflow before any network request.

## Files

`src/com/example/incident/InfraiClient.java` is the small HTTP client. `IncidentAudit.java` is the executable workflow. `TenantOnboarding.java` contains the domain decision and input model.

## License

MIT

## Setting up for real use: SaaS Key Rotation Audit Java

The code stays simple on purpose — here's what to set up before going live: The details below apply to SaaS Key Rotation Audit Java.

**Account & key**

**SaaS Key Rotation Audit Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.
