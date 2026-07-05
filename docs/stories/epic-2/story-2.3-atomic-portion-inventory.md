# Story 2.3: Atomic Portion Inventory Service & Live Counts

**Epic:** 2 — Menus & Portion Inventory    **Status:** Draft
**Traces:** FR6, FR8, NFR1, NFR2

## Story
As a buyer, I want dish portion counts to be accurate and live, so that I never pay for
food that's already sold out — even when many people order at once.

## Acceptance Criteria
1. Given a MenuItem with `portionsRemaining = 3`, When two concurrent orders request 2
   portions each, Then exactly one succeeds and the other receives PORTIONS_CONFLICT;
   final remaining = 1. (Race test required, NFR1.)
2. Given an order is cancelled or declined, Then its portions are restored in the same
   transaction as the status change.
3. Given a kitchen profile open in a browser, When remaining counts change, Then the UI
   updates within 3 s via SSE `/kitchens/:id/portions/stream` (fallback: 10 s polling).
4. A seller can manually correct counts (+/-) with an AuditLog entry; manual decrement
   below committed-order quantity is rejected.

## Dev Notes (embedded context)
- Core operation (architecture.md › Key Workflows 1):
  `UPDATE menu_items SET portions_remaining = portions_remaining - $qty
   WHERE id = $id AND portions_remaining >= $qty` — rowcount 0 ⇒ conflict; never read-
  then-write.
- Entities: `MenuItem(id, menuDayId, dishId, portionsTotal, portionsRemaining)`.
- Files: `packages/core/src/services/inventory.service.ts`,
  `apps/api/src/inventory/{inventory.module,inventory.controller,portions.sse.ts}`,
  k6 script `apps/api/test/load/oversell.k6.js`, migration `0006_menu_items`.
- SSE publishes via Redis pub/sub channel `portions:{kitchenId}`.

## Tasks
- [ ] InventoryService.decrement/restore with conditional UPDATE
- [ ] Wire into OrderService.place/cancel transaction boundaries
- [ ] SSE endpoint + Redis pub/sub fanout
- [ ] Seller manual correction endpoint with floor validation
- [ ] k6 race test (50 VUs vs 10 portions) added to CI nightly

## Testing Requirements
- Integration: AC1 with parallel transactions (pg, not mocks)
- Property test: total committed + remaining == portionsTotal across random op sequences
