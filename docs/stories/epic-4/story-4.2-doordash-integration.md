# Story 4.2: DoorDash Drive Delivery Integration

**Epic:** 4 — Fulfillment & Order Lifecycle    **Status:** Draft
**Traces:** FR9, NFR8, NFR10

## Story
As a buyer who chose delivery, I want my order handed to a DoorDash courier when the
seller marks it Ready, so that home-cooked food arrives without the seller leaving home.

## Acceptance Criteria
1. Given a confirmed delivery order, When checkout requested a quote, Then the quote
   came from DoorDash Drive `POST /quotes` through the provider interface.
2. Given the seller marks the order Ready, Then a delivery is created (accepting the
   quote), a DeliveryJob row stores externalId + trackingUrl, and the buyer is notified
   with the tracking link.
3. Given DoorDash webhooks (courier assigned, picked up, delivered, cancelled), Then
   DeliveryJob.status updates, signatures are verified, and events are idempotent
   (replayed webhook causes no duplicate state change).
4. Given DoorDash returns 5xx or times out on create, Then BullMQ retries with backoff
   (3 attempts); on final failure seller and buyer are notified and the order falls back
   to seller-coordinated handoff state DELIVERY_FAILED_MANUAL.

## Dev Notes (embedded context)
- Provider interface (from Story 3.3): `quote(pickup, dropoff, items) → {feeCents,
  quoteId}`, `create(quoteId, orderRef)`, `parseWebhook(req)`.
- Files: `packages/providers/src/delivery/doordash.provider.ts`,
  `apps/api/src/delivery/{delivery.module,delivery.webhooks.controller,delivery.processor.ts}`,
  migration `0009_delivery_jobs`.
- Entities: `DeliveryJob(id, orderId, provider, externalId, status, trackingUrl, feeCents)`.
- Auth: DoorDash Drive JWT (developer credentials in env); sandbox first.

## Tasks
- [ ] DoorDash provider: quote/create/cancel + JWT auth
- [ ] Webhook controller with signature verification + idempotency (event id store)
- [ ] BullMQ delivery queue + retry/backoff + failure notifications
- [ ] Status mapping table DoorDash → DeliveryJob.status
- [ ] AuditLog on all DeliveryJob mutations

## Testing Requirements
- Contract tests against recorded sandbox responses (nock fixtures)
- Idempotency test: same webhook delivered twice → single transition
- Failure drill: forced 503s exhaust retries → DELIVERY_FAILED_MANUAL + notifications
