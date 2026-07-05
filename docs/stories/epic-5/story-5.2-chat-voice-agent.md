# Story 5.2: In-App Conversational Agent (Chat + Voice)

**Epic:** 5 — Conversational Agent & MCP    **Status:** Draft
**Traces:** FR12, FR13, FR15, NFR4, NFR9

## Story
As a buyer, I want to type or speak "order 2 portions of manti from Ayşe's kitchen for
6pm pickup" and have the agent handle everything, so that I never need to tap through
menus.

## Acceptance Criteria
1. Given a chat message, Then the agent (Claude with the Story 5.1 MCP tools) can
   complete discovery → menu → order in conversation, streaming replies.
2. Before purchase, the agent renders a structured summary card (items, qty, total incl.
   fees, ready-time, fulfillment, pickup/delivery address) and requires an explicit
   "Confirm" tap or affirmative reply; only then does it call
   `create_order(confirm:true)` (FR15 — also server-enforced).
3. Given a voice message (≤60 s), Then it is transcribed and handled identically to text;
   p95 from upload-complete to first agent token < 5 s (NFR4).
4. Given a low-confidence transcription (< provider threshold), Then the agent asks a
   clarifying question instead of acting (per front-end-spec states).
5. The agent responds in the user's locale where supported (NFR9) and gracefully hands
   off to UI deep links on repeated tool failure.

## Dev Notes (embedded context)
- Files: `apps/agent/src/{agent.service.ts,prompts/system.md,stt/stt.provider.ts}`,
  `apps/api/src/chat/{chat.gateway.ts}` (SSE/WebSocket streaming),
  `apps/web/app/chat/page.tsx` with summary-card component.
- LLM: Claude API messages with MCP connector to apps/mcp-server; temperature low;
  system prompt forbids inventing dishes and mandates the confirmation step.
- STT provider interface in packages/providers/src/stt (Whisper API impl + mock).
- Audio: client uploads m4a/webm to S3 presigned URL; agent service pulls + transcribes.

## Tasks
- [ ] Agent service: Claude tool-use loop against MCP server, streamed to client
- [ ] System prompt with confirmation + no-hallucination policy (tested)
- [ ] Voice pipeline: presigned upload → STT → agent; latency metric + alert
- [ ] Summary card UI + confirm interaction (tap or affirmative)
- [ ] Locale passthrough + UI fallback deep links

## Testing Requirements
- Scenario tests (recorded tool fixtures): happy order, sold-out mid-conversation
  (PORTIONS_CONFLICT recovery), ambiguous kitchen name disambiguation
- Guardrail test: agent transcript that skips confirmation must fail CI
- Latency test on voice path against NFR4 budget
