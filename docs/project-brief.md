# Project Brief: Nanas' Kitchens — Home Kitchen Cultural Cuisine Marketplace

## Executive Summary
Nanas' Kitchens is a hyperlocal marketplace that lets home cooks (housewives, hobby chefs,
anyone passionate about their heritage cuisine) sell authentic cultural meals to buyers
within a 10-mile radius. Each kitchen is culture-themed (a Chinese home cook runs a
Chinese kitchen, a Turkish home cook a Turkish kitchen). Buyers order via a classic app
UI **or** conversationally — typing or speaking to an LLM agent that places orders
through MCP tools. Orders are scheduled for a requested ready-time and fulfilled by
pickup at the seller's home or delivery through DoorDash/Grubhub. Live remaining-portion
counts prevent overselling, and trust is built through kitchen photos, buyer feedback,
seller-uploaded health reports, and an independent hygiene inspection score.

## Problem Statement
Talented home cooks have no low-friction, trusted channel to monetize authentic cultural
cooking; restaurant platforms exclude them. Buyers craving authentic home-style ethnic
food can't find it nearby, can't verify a home kitchen's hygiene, and existing food apps
don't handle small-batch ("only 8 portions today") scheduled home cooking.

## Proposed Solution
A two-sided marketplace with: culture-tagged kitchen profiles, small-batch menus with
live portion inventory, ready-time scheduling, pickup or third-party delivery, a
conversational ordering agent (chat + voice, MCP-based), community features (menu polls,
cuisine requests, photos, reviews), and a two-layer trust system (self-published health
reports + independent inspector hygiene score).

## Target Users
### Primary
- **Home-cook sellers**: skilled home cooks of a specific cultural cuisine, cooking from
  home, low tech proficiency, want simple menu/order/portion management.
- **Local buyers**: people within 10 miles seeking authentic, home-made cultural food;
  includes diaspora communities and adventurous eaters.
### Secondary
- **Independent hygiene inspectors**: vetted third parties who visit kitchens and submit
  cleanliness/health scores via their own portal.

## Goals & Success Metrics
| Goal | Metric | MVP Target |
|---|---|---|
| Liquidity | Active kitchens per launch zip cluster | ≥ 25 |
| Demand | Orders per active kitchen per week | ≥ 10 |
| Sell-through | % of published portions sold | ≥ 70% |
| Agent adoption | % of orders placed via chat/voice agent | ≥ 20% |
| Trust | % of active kitchens with inspector score | ≥ 60% within 90 days |

## MVP Scope
### Core Features (Must Have)
1. Seller onboarding with culture-tagged kitchen profile, photos, address (geocoded)
2. Menu management: dishes, prices, daily portion counts, available ready-time windows
3. Buyer discovery: kitchens/dishes within 10 miles, filter by cuisine
4. Ordering with ready-time selection; live remaining-portion display and decrement
5. Fulfillment choice: pickup at seller's home OR delivery via DoorDash Drive /
   Grubhub API
6. Conversational ordering agent: chat prompt and voice message; agent uses MCP tools
   (search kitchens, get menu, check portions, place order, track order)
7. Community: kitchen feedback/ratings, menu polls, "request a dish" cuisine offers
8. Trust: seller uploads health reports; inspector portal to submit visit scores;
   score badge on kitchen profile
9. Payments via hosted provider (Stripe Connect) with platform commission

### Out of Scope for MVP
- Platform-owned delivery fleet; real-time courier tracking beyond partner deep links
- Subscriptions/meal plans, catering, multi-kitchen carts
- Automated legal compliance per jurisdiction (cottage-food laws shown as guidance only)
- In-app video, live streaming, loyalty programs

## Constraints & Assumptions
- Assumption: launch in US metros where cottage food / home kitchen operations (MEHKO)
  laws permit home food sales; sellers self-attest compliance at onboarding.
- Constraint: delivery is exclusively via partner APIs (DoorDash Drive, Grubhub) — no
  in-house logistics.
- Assumption: "agent skills / LLM MCP" requirement means the platform must expose a
  first-party MCP server so any LLM client (including the in-app agent) can transact.
- Voice ordering = speech-to-text feeding the same agent (no custom wake-word device).

## Risks & Open Questions
- Regulatory variance by state/county for home food sales (mitigation: launch-market
  allowlist + attestation).
- Inspector network supply: who recruits/vets inspectors? (MVP: platform-invited only.)
- Oversell race conditions on portions during demand spikes (architecture must enforce
  atomic decrement).
- Delivery partner API access requires commercial agreements; sandbox-first build.

## Next Step
PM: derive PRD with numbered FR/NFRs; pay special attention to portion-inventory
consistency, the MCP tool surface, and the inspector workflow.
