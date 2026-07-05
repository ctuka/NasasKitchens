# Story 1.4: Cottage-Food Compliance Attestation Gate

**Epic:** 1 — Foundation & Kitchen Onboarding    **Status:** Draft
**Traces:** FR2, NFR10

## Story
As the platform, I want sellers to attest to local home-food-sale compliance before they
can publish anything, so that only self-certified kitchens go live in allowed markets.

## Acceptance Criteria
1. Given a seller in an allowlisted launch market (config table of allowed
   states/counties), When they read the jurisdiction guidance text and check the
   attestation box, Then `complianceAttestedAt` is set with timestamp and request IP,
   and an AuditLog entry is written.
2. Given a kitchen without attestation, When the seller attempts to publish a menu
   (Epic 2 endpoint), Then 403 ATTESTATION_REQUIRED.
3. Given a seller outside allowlisted markets, Then onboarding shows a "not yet
   available in your area" state and blocks attestation.

## Dev Notes (embedded context)
- Config: `LaunchMarket(id, state, county?, guidanceMarkdown)` admin-managed.
- Files: `apps/api/src/compliance/{compliance.module,compliance.service,
  compliance.controller}.ts`, guard `AttestationGuard` exported for menus module,
  `apps/web/app/seller/onboarding/attestation/page.tsx`, migration `0004_compliance`.
- Endpoint: POST /kitchens/:id/attestation; GET /launch-markets?lat&lng.

## Tasks
- [ ] LaunchMarket model + admin CRUD + seed for pilot markets
- [ ] Attestation endpoint storing timestamp + IP
- [ ] Reusable AttestationGuard
- [ ] Onboarding UI with guidance text + blocked state

## Testing Requirements
- e2e: publish attempt pre-attestation → 403; post-attestation → passes guard
- Audit entry asserted on attestation
