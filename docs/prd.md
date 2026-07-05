# Product Requirements Document: Nanas' Kitchens

## Goals and Background Context
Derived from docs/project-brief.md. Two-sided hyperlocal marketplace for home-cooked
cultural cuisine within a 10-mile radius, with classic UI ordering and an LLM/MCP
conversational ordering channel, partner-based delivery, and a two-layer hygiene trust
system. No changes to Brief scope.

## Requirements

### Functional
- **FR1**: A seller can create a kitchen profile with name, description, one primary
  culture/cuisine tag, kitchen photos, and a home address that is geocoded on save.
- **FR2**: A seller can attest to local cottage-food/MEHKO compliance during onboarding;
  un-attested kitchens cannot publish menus.
- **FR3**: A seller can create menus containing dishes with name, description, photo,
  price, dietary tags, and a per-day portion count.
- **FR4**: A seller can define ready-time windows (e.g., 17:00–20:00 in 30-min slots)
  per menu day.
- **FR5**: A buyer can browse/search kitchens and dishes within 10 miles of their
  location, filtered by cuisine, with distance shown.
- **FR6**: Every dish displays its live remaining portion count; counts decrement
  atomically at order confirmation and restore on cancellation.
- **FR7**: A buyer can place an order selecting dishes, quantities, a ready-time slot,
  and fulfillment mode (pickup or delivery).
- **FR8**: An order cannot be confirmed if requested quantity exceeds remaining portions
  (no oversell, including under concurrent orders).
- **FR9**: For delivery orders, the system creates a delivery job via DoorDash Drive or
  Grubhub APIs and surfaces status updates and tracking links to buyer and seller.
- **FR10**: For pickup orders, the seller's pickup address and instructions are revealed
  to the buyer only after order confirmation.
- **FR11**: A seller can view incoming orders by ready-time, accept/decline within a
  time limit, and mark orders Preparing → Ready → Completed.
- **FR12**: A buyer can place a complete order through a conversational chat interface
  without using any other UI screen.
- **FR13**: A buyer can place an order by voice message; speech is transcribed and
  handled by the same conversational agent as FR12.
- **FR14**: The platform exposes a first-party MCP server with tools at minimum:
  `search_kitchens`, `get_menu`, `check_portions`, `create_order`, `get_order_status`,
  `cancel_order` — usable by the in-app agent and by external LLM clients after OAuth.
- **FR15**: The conversational agent must confirm an order summary (items, total,
  ready-time, fulfillment, address) and receive explicit user confirmation before
  calling `create_order`.
- **FR16**: A buyer can rate (1–5) and review a kitchen only after a completed order.
- **FR17**: A seller can publish polls for candidate upcoming menus; buyers within range
  can vote once per poll.
- **FR18**: A buyer can submit a "dish request / cuisine offer" to a kitchen; the seller
  can accept it into a future menu, notifying the requester.
- **FR19**: A seller can upload health/permit documents (PDF/image) to their portal,
  displayed on their kitchen profile with upload date.
- **FR20**: An inspector (role-gated portal) can be assigned a kitchen visit, submit a
  structured cleanliness/health score (0–100 + sub-scores), and the resulting badge and
  date appear on the kitchen profile.
- **FR21**: Buyers pay in-app at order time; sellers are paid out via Stripe Connect
  minus platform commission; declined/cancelled orders are refunded automatically.
- **FR22**: Buyers and sellers receive notifications (push/email) for order lifecycle
  events, poll results, and accepted dish requests.

### Non-Functional
- **NFR1**: Portion inventory operations must be strongly consistent — zero oversells
  under concurrent load (verified by race-condition tests).
- **NFR2**: Geo searches (10-mile radius) return in < 500 ms p95 for 10k kitchens.
- **NFR3**: The MCP tool surface is the same API the UI uses (one source of truth);
  no order path may bypass inventory and payment checks.
- **NFR4**: Voice transcription round-trip (audio → agent reply begins) < 5 s p95.
- **NFR5**: PII (home addresses) is encrypted at rest; pickup address visibility follows
  FR10; inspectors see addresses only for assigned visits.
- **NFR6**: PCI scope is delegated to Stripe (no card data touches our servers).
- **NFR7**: Web app meets WCAG 2.1 AA; conversational channel provides an accessible
  alternative to visual flows.
- **NFR8**: All third-party calls (DoorDash, Grubhub, Stripe, STT, LLM) have timeouts,
  retries with idempotency keys, and graceful degradation paths.
- **NFR9**: System is localizable (i18n strings, RTL-ready) given multicultural users.
- **NFR10**: Audit log for all order, payout, and inspection-score mutations.

## User Roles & Key Flows
| Role | Description |
|---|---|
| Buyer | Orders food via UI or agent |
| Seller | Runs a culture-tagged home kitchen |
| Inspector | Submits hygiene scores via portal |
| Admin | Manages launch markets, inspector assignments, disputes |

Defining flows: (1) Buyer discovery → order → fulfillment; (2) Conversational order via
agent/MCP; (3) Seller daily menu publish → order management; (4) Inspection lifecycle.

## Epic List
| # | Epic | Goal | Key FRs |
|---|---|---|---|
| 1 | Foundation & Kitchen Onboarding | Repo/app scaffold, auth, seller profiles live | FR1, FR2 |
| 2 | Menus & Portion Inventory | Publishable menus with atomic portion counts | FR3, FR4, FR6 |
| 3 | Discovery & Classic Ordering | Geo search, cart, checkout, payments | FR5, FR7, FR8, FR10, FR21 |
| 4 | Fulfillment & Order Lifecycle | Seller order mgmt, delivery partner integration, notifications | FR9, FR11, FR22 |
| 5 | Conversational Agent & MCP | MCP server, chat + voice ordering | FR12–FR15 |
| 6 | Community & Engagement | Reviews, polls, dish requests | FR16–FR18 |
| 7 | Trust & Health Scoring | Health report uploads, inspector portal & badges | FR19, FR20 |

## Epic Details

### Epic 1: Foundation & Kitchen Onboarding
Goal: stand up the monorepo, CI, auth, and database, and deliver real value: a seller can
register, attest compliance, and publish a kitchen profile visible at a public URL.
- 1.1 Project scaffolding, CI, environments
- 1.2 Auth & roles (buyer/seller/inspector/admin)
- 1.3 Kitchen profile CRUD with photos + geocoding
- 1.4 Compliance attestation gate

### Epic 2: Menus & Portion Inventory
Goal: sellers publish dated menus with portion counts; inventory layer guarantees
atomic decrement/restore.
- 2.1 Dish & menu CRUD
- 2.2 Ready-time window configuration
- 2.3 Portion inventory service (atomic, race-safe) + live counts API

### Epic 3: Discovery & Classic Ordering
Goal: buyers find nearby kitchens and complete paid orders in the UI.
- 3.1 Geo search (PostGIS) + cuisine filters
- 3.2 Kitchen & dish detail pages with live portions
- 3.3 Cart + checkout with ready-time slot selection
- 3.4 Stripe Connect payments, commission, refunds
- 3.5 Post-confirmation pickup-address reveal

### Epic 4: Fulfillment & Order Lifecycle
Goal: sellers manage orders end-to-end; delivery handled by partners.
- 4.1 Seller order dashboard (accept/decline, status transitions)
- 4.2 DoorDash Drive integration (quote, create, webhooks)
- 4.3 Grubhub integration + provider abstraction/fallback
- 4.4 Notifications (push/email) for lifecycle events

### Epic 5: Conversational Agent & MCP
Goal: ordering without the UI, via chat or voice, through a first-party MCP server.
- 5.1 MCP server exposing the six core tools over the existing API
- 5.2 In-app chat agent with order-confirmation guardrail (FR15)
- 5.3 Voice message pipeline (STT → agent → reply)
- 5.4 External-client OAuth for MCP access

### Epic 6: Community & Engagement
Goal: feedback loops that drive repeat demand.
- 6.1 Ratings & reviews (post-completion only)
- 6.2 Menu polls with single-vote enforcement
- 6.3 Dish requests / cuisine offers with seller accept flow

### Epic 7: Trust & Health Scoring
Goal: visible hygiene trust signals.
- 7.1 Health-report document upload & display
- 7.2 Inspector portal: assignments, structured scoring, badge rendering
- 7.3 Admin tools for inspector management & score disputes

## Out of Scope (MVP)
As per Brief: own delivery fleet, subscriptions, catering, multi-kitchen carts,
automated per-jurisdiction compliance, loyalty, live video.

## Assumptions
- Launch-market allowlist limited to MEHKO/cottage-food-friendly jurisdictions.
- DoorDash Drive is the primary delivery provider; Grubhub secondary behind a common
  provider interface.
- Inspectors are platform-invited in MVP (no open signup).

## Handoff
Architect: NFR1 (inventory consistency), NFR3 (single API surface shared by UI and MCP)
and NFR8 (partner resilience) are the binding constraints. UX: design for low-tech
sellers and for buyers who may never open a visual menu (agent-first parity).
