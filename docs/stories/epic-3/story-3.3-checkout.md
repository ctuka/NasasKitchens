# Story 3.3: Cart & Checkout with Ready-Time and Fulfillment Choice

**Epic:** 3 — Discovery & Classic Ordering    **Status:** Draft
**Traces:** FR7, FR8, FR10, FR21, NFR6, NFR8

## Story
As a buyer, I want to pick dishes, choose a ready-time slot and pickup-or-delivery, and
pay, so that my food is prepared exactly when I want it.

## Acceptance Criteria
1. Given a cart with items from one kitchen, When I open checkout, Then I see only
   ready-time slots within the kitchen's published windows for that menu day.
2. Given fulfillment = Delivery, Then a fee quote is fetched from the delivery provider
   (3 s timeout); on timeout/failure, the UI offers Pickup as fallback (NFR8).
3. Given I confirm payment (Stripe PaymentSheet), Then portions are decremented (Story
   2.3 service) and the Order is created atomically; on payment failure portions are
   restored.
4. Given fulfillment = Pickup, Then the seller's street address and pickup instructions
   appear only on the confirmed-order screen, never before (FR10).
5. Order total = items + delivery fee; commission recorded per architecture
   (`commissionCents` on Order) for payout math (FR21).

## Dev Notes (embedded context)
- Entities: Order, OrderItem (architecture.md › Data Model); idempotencyKey = cart draft
  id reused as Stripe idempotency key.
- Files: `apps/api/src/orders/{orders.module,orders.service,orders.controller}.ts`,
  `apps/web/app/checkout/page.tsx`, `packages/providers/src/delivery/
  {delivery-provider.interface,mock.provider}.ts` (real providers in Epic 4).
- Endpoints: POST /orders (draft), POST /orders/:id/confirm, GET /orders/:id.
- Stripe: PaymentIntent with transfer_data to seller's Connect account, application_fee.

## Tasks
- [ ] Order draft + slot validation against MenuDay.readyWindows
- [ ] Delivery quote via provider interface with timeout + pickup fallback
- [ ] Confirm flow: decrement → PaymentIntent → compensating restore on failure
- [ ] Address-reveal rule in order detail serializer
- [ ] Checkout UI states incl. PORTIONS_CONFLICT dialog (per front-end-spec)

## Testing Requirements
- e2e happy paths: pickup and delivery (mock provider)
- Failure paths: quote timeout → pickup fallback; payment fail → portions restored
- Serializer test: address absent pre-confirmation, present post-confirmation
