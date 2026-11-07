# Node.js Customer Usage Metering — Billing Attribution Across 2 Rotating API Keys

Rotating a production API key creates a tension between service continuity and billing attribution: an overlap interval lets running workers finish, but it also divides one customer's consumption across credentials that may appear as separate reporting identities. Resolve that tension by keeping the customer identity stable while recording the credential version separately. Short answer: for per-customer API usage metering in a Node.js marketplace, use a reconciled usage ledger as the billing source of truth; treat platform counters and application counters as evidence whose authority depends on their attribution, unit, and completion semantics.

Keep identity stable.

This note develops a proposed design for metered SaaS billing through one concrete operation: replacing a production credential while requests continue. The numerical examples are illustrative acceptance cases, not observations of a deployed service. The decision axis is attribution accuracy, since a matching aggregate total cannot establish that the right marketplace seller received the charge.

## How should per-customer API usage metering preserve billing attribution during key rotation?

Begin with the chargeable event, before choosing a counter. Suppose the marketplace contract defines one billable unit as one completed operation for a seller, regardless of how many transport attempts were needed to deliver it. That is an example policy, not an assumption about an external API. It requires a stable operation identifier, a seller identifier established by the marketplace's authenticated context, a recorded completion decision, and an explicit policy version; the credential used for an outbound request is evidence about execution, whereas the seller identifier determines the intended billing attribution.

Those identities have different lifetimes.

A proposed event might associate seller `seller_017`, operation `op_841`, and credential version `key_v1`. A later attempt for that same operation might use `key_v2`. Neither transition should create a second billable operation, and neither credential should become a replacement for the seller identifier. Where one outbound credential serves several sellers, a report grouped only by credential cannot, on its own, distinguish those sellers; additional request-level attribution or application evidence is necessary to reconstruct that split.

The billing invariant is therefore precise: each accepted completion contributes its defined quantity once to its attributed seller, while every execution attempt remains traceable. An exactly-once mindset belongs at that acceptance boundary. It does not justify a claim that the network delivers requests exactly once, nor that a local uniqueness constraint controls an external service's execution.

## Derive the ledger from the evidence you need

Separate an attempt journal from the accepted usage ledger. The journal records an outbound attempt, including its operation identifier, credential version, request correlation identifier when available, and observed outcome. The ledger records the accepted chargeable event with its customer identity, unit, quantity, event time, policy version, and evidence reference. A retry can extend the journal without extending the bill. A later billing correction should reference the original event through an adjustment rather than silently rewriting its attribution.

This separation costs storage and reconciliation work. It also gives a billing reviewer a way to answer a much narrower question than “does the dashboard total look right?”: which completion caused this seller's invoice line, and what evidence supports that decision?

For the illustrative operation above, two attempts could produce one accepted completion. If the external system reports two consumed units because it measures attempts, a difference of one is an expected policy difference, not automatically a dropped event. If it reports completed operations using the same definition and boundary, that same difference needs investigation. Record the explanation with the reconciliation result; an aggregate discrepancy without its unit and scope is not yet a useful accounting finding.

The unresolved outcome is the difficult case. A worker may lose the response after an operation has been accepted remotely. Mark that attempt as unresolved and seek a correlated completion record before accepting a charge. Where the remote interface supports idempotency, preserve the logical operation identifier across retries according to that interface's documented rules; changing the outbound credential must not casually change the operation identity. Where that support is absent or its scope is unknown, local deduplication alone cannot establish whether retrying will repeat the remote effect.

Do not guess.

A Node.js application can submit completion evidence to a language-independent ledger boundary. The following Go example defines that boundary, rather than prescribing a commercial integration or pretending that an in-memory counter is durable billing storage. Its event identifier must come from the authoritative completion workflow and remain stable across redelivery.

```go
package metering

import (
	"context"
	"errors"
)

type Completion struct {
	EventID       string
	SellerID      string
	OperationID   string
	Unit          string
	PolicyVersion string
	EvidenceRef   string
	Quantity      int64
}

type Ledger interface {
	// AcceptOnce atomically commits an event or recognizes an exact replay.
	// An existing EventID with different fields must return a conflict error.
	// Persistence errors must be returned; nil means durable acceptance.
	AcceptOnce(context.Context, Completion) error
}

func RecordCompletion(ctx context.Context, ledger Ledger, c Completion) error {
	if c.EventID == "" || c.SellerID == "" || c.OperationID == "" ||
		c.Unit == "" || c.PolicyVersion == "" || c.EvidenceRef == "" ||
		c.Quantity <= 0 {
		return errors.New("incomplete or invalid completion evidence")
	}
	return ledger.AcceptOnce(ctx, c)
}
```

The interface has deliberately strong requirements. Its implementation needs a durable uniqueness boundary and a comparison of the immutable event fields inside the acceptance transaction; silently ignoring any repeated identifier would conceal a conflicting seller or quantity. A caller must preserve pending evidence until acceptance succeeds, and recover it after a restart. This code leaves persistence, authentication, completion verification, and recovery to the surrounding system, so it is a focused contract example rather than a complete billing service.

Notice that the completion contains no secret and no credential version. The evidence reference leads to the restricted attempt journal, where credential metadata belongs. This keeps the charge identity independent of secret replacement while retaining the audit trail needed to explain the rotation.

## Compare counters only after defining their authority

“Platform counters versus own counters” becomes a useful comparison once both sides have a declared unit, customer mapping, time boundary, and correction policy. Owning a counter does not make it authoritative, and an external report does not make a credential identifier equivalent to a customer. In the proposed design, the accepted ledger determines invoice quantities only after its reconciliation controls have been satisfied.

| Evidence | Authority in this design | Boundary to verify |
| --- | --- | --- |
| Platform usage report | Corroborate externally observed consumption | Does it expose the needed seller mapping, unit, finality, and reporting interval? |
| Application attempt counter | Track dispatch and retry activity | Does it count attempts, accepted work, or completed work? |
| Accepted usage ledger | Derive billable quantities under the recorded policy | Are acceptance, deduplication, corrections, and attribution auditable? |

Consider an illustrative interval containing 7 completed operations for one seller: 4 observed through the old credential and 3 through the replacement. A credential-level report with those quantities can corroborate the seller total only if the recorded mapping supports that attribution. If both credentials serve several sellers, summing 4 and 3 proves an aggregate of 7 in the example; it does not prove any particular seller's share. Never repair that missing dimension by dividing consumption proportionally unless the billing contract explicitly defines such an allocation.

The practical decision rule is to choose the least ambiguous evidence for each assertion. Use authenticated marketplace context to establish the seller, verified completion evidence to establish the chargeable event, and external usage evidence to check the consumption dimension it actually exposes. If a platform report offers sufficiently granular, stable, and documented attribution, it may supply primary completion evidence. If it offers only credential totals, retain it as an aggregate reconciliation input and require a separate customer attribution trail.

Do the comparison before invoicing, including records that arrive after the initial collection window. Define the close policy and the handling of late adjustments explicitly; otherwise, rotating at a reporting boundary can turn a timing difference into an unexplained charge. Monitoring should distinguish unresolved attempts, duplicate deliveries, conflicting event identifiers, unknown credential versions, and attribution mismatches. One combined “usage error” metric would hide the decisions an operator must make.

## Secret lifecycle controls do not certify a billing ledger

The OWASP Secrets Management Cheat Sheet discusses secret rotation, revocation, access control, and auditing. Apply that lifecycle lens to production key replacement: restrict who can obtain the replacement, record its deployment and retirement as credential metadata, and avoid placing secret values in metering events or diagnostic logs. The rotation audit trail should identify the actor and credential version without reproducing the credential.

That guidance is a security reference, not evidence that this proposed ledger meets a particular financial, privacy, or regulatory obligation. Retention, permitted customer identifiers, access to billing evidence, and the treatment of corrections require review against the marketplace's actual contractual and jurisdictional requirements. An append-only design does not itself establish compliance, and storing every request body indefinitely is not a prerequisite for explaining a charge.

Choose the minimum evidence that still supports the billing decision. This creates a real trade-off: richer correlation can make reconciliation easier, while broader access and longer retention expand the data that must be governed. Document the chosen boundary so support staff can investigate a disputed operation without receiving production secrets or unrelated seller data.

## Roll out with overlap, then prove retirement is safe

For the illustrative two-key migration, first verify that the upstream credential lifecycle permits simultaneous validity. If it does, provision the replacement, add its version to the attempt journal's recognized metadata, and move a limited set of workers while preserving operation and seller identities. An overlap design depends on that lifecycle capability; without it, uninterrupted replacement requires a different supported transition mechanism and cannot be inferred from the ledger design.

Test the attribution boundary before expanding the rollout: deliver the same completion twice, retry an unresolved operation using the replacement credential, submit a conflicting seller under an existing event identifier, restart a worker with pending evidence, and reconcile an event reported after the interval closes. The expected results follow from the contract: exact replays add no charge, conflicts require investigation, and unresolved work remains pending until evidence supports a decision. These are proposed acceptance tests, not claims of measured production behavior.

Then expand deployment and inspect usage by both seller and credential version. The old credential is ready for revocation when the migration inventory accounts for its consumers and the relevant in-flight work has been resolved or assigned an explicit recovery path; a quiet reporting graph alone cannot establish that every worker has stopped using it. Keep reconciliation running after retirement to catch late evidence. This work adds operational burden, but it preserves the distinction that matters to a marketplace invoice: the customer owns the billable event, and a replaceable API key records how an attempt was authorized.

## Sources

References: the source below supports the secret lifecycle discussion. The ledger boundary, sample identifiers, numerical examples, and rollout acceptance criteria above are proposed engineering choices, not claims that the source specifies a billing architecture.

- OWASP Secrets Management Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html
