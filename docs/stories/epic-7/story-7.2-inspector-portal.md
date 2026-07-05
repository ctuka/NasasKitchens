# Story 7.2: Inspector Portal — Visits, Structured Scoring & Badges

**Epic:** 7 — Trust & Health Scoring    **Status:** Draft
**Traces:** FR20, NFR5, NFR10

## Story
As an independent inspector, I want to receive kitchen visit assignments and submit a
structured cleanliness/health score, so that buyers see a trustworthy hygiene badge on
each kitchen.

## Acceptance Criteria
1. Given an admin assigns a visit, Then the inspector sees it in "Assigned Visits" with
   kitchen name and — only for assigned visits — the street address (NFR5 scoping).
2. The scoring form captures sub-scores (storage, prep surfaces, temperature control,
   personal hygiene, documentation; each 0–20) auto-summing to 0–100, plus photo
   evidence; drafts persist offline (IndexedDB) and sync on reconnect.
3. Given submission, Then the HygieneScore is locked (submit-once), the kitchen profile
   badge updates (score + visit date), and an AuditLog entry is written; later edits
   require an admin-mediated dispute flow (Story 7.3).
4. Kitchens without a score show "Not yet inspected" — never a fabricated value.

## Dev Notes (embedded context)
- Entities: `InspectionVisit(id, kitchenId, inspectorId, scheduledAt, status)`,
  `HygieneScore(id, visitId UNIQUE, total, subScores jsonb, photos[], submittedAt)`;
  badge denormalized to Kitchen.hygieneScoreId.
- Files: `apps/api/src/trust/{trust.module,inspections.controller,scores.controller}.ts`,
  `apps/web/app/inspector/{visits,score/[visitId]}/page.tsx` (PWA offline draft),
  migration `0012_inspections`.
- Endpoints: GET /inspections/assigned (inspector), POST /inspections/:id/score,
  POST /inspections (admin assign).
- Address exposure: order/inspection serializers are the only places decrypting
  addresses; assert via shared serializer tests.

## Tasks
- [ ] Models + admin assignment endpoint
- [ ] Inspector-scoped visit list with address decryption rule
- [ ] Scoring form with sub-score validation + photo upload + offline draft
- [ ] Submit-once lock + badge denormalization + AuditLog
- [ ] Profile badge component (score, date, "Not yet inspected" state)

## Testing Requirements
- AuthZ: inspector A cannot read inspector B's visits; unassigned kitchen address never
  returned
- Submit-once: second POST → 409
- Offline draft e2e (Playwright with network off/on)
