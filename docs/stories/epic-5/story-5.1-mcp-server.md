# Story 5.1: First-Party MCP Server with Core Ordering Tools

**Epic:** 5 — Conversational Agent & MCP    **Status:** Draft
**Traces:** FR12, FR14, FR15, NFR3

## Story
As an LLM client (the in-app agent or an external assistant), I want MCP tools to search
kitchens, read menus, check portions, and place orders, so that a buyer can transact
entirely through conversation.

## Acceptance Criteria
1. The MCP server exposes exactly: `search_kitchens(lat, lng, cuisine?)`,
   `get_menu(kitchenId, date?)`, `check_portions(menuItemIds[])`,
   `create_order(draft, confirm)`, `get_order_status(orderId)`, `cancel_order(orderId)`
   — each delegating to the same `packages/core` service the REST API uses (NFR3; no
   duplicated business logic).
2. Given `create_order` with `confirm != true`, Then the server returns the priced order
   summary WITHOUT placing the order; only `confirm: true` places it (server-enforced
   FR15 guardrail).
3. Given an order draft containing a menuItemId not present in the kitchen's published
   menu for that date, Then the tool returns a validation error (anti-hallucination
   check from architecture risks).
4. Tool calls are scoped to the authenticated buyer; one buyer cannot read or cancel
   another buyer's order.
5. All tools enforce the same inventory/payment paths as REST (an order via MCP shows
   identically in the seller dashboard and Stripe).

## Dev Notes (embedded context)
- SDK: `@modelcontextprotocol/sdk` 1.x, streamable HTTP transport.
- Files: `apps/mcp-server/src/{server.ts,tools/*.ts,auth.middleware.ts}` importing
  services from `packages/core` (KitchenService.search, MenuService.getPublished,
  InventoryService.remaining, OrderService.place/status/cancel).
- Session auth in this story = platform JWT passed as bearer; OAuth 2.1 for external
  clients is Story 5.4.
- Tool schemas: zod, mirrored from REST DTOs in packages/core (single source).

## Tasks
- [ ] MCP server bootstrap + transport + JWT auth middleware
- [ ] Implement six tools as thin service adapters
- [ ] confirm-flag guardrail + menu-snapshot validation in OrderService (shared)
- [ ] Buyer-scoping authorization tests
- [ ] Parity test: same order via REST and via MCP produces identical rows

## Testing Requirements
- MCP integration tests via SDK client harness covering AC1–AC5
- Negative: unconfirmed create_order never decrements portions
