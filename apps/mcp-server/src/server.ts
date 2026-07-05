#!/usr/bin/env node
/**
 * CulturEats MCP Server — Story 5.1 / chatbot interface.
 *
 * Exposes 6 ordering tools + 1 ordering-assistant prompt.
 * All tools delegate to the CulturEats REST API (NFR3: single business-logic path).
 * FR15 confirmation guardrail is enforced server-side; the MCP layer passes confirm through.
 *
 * Transports:
 *   stdio  (default) — for Claude Desktop / local clients
 *   http   (TRANSPORT=http) — for remote / multi-client deployments
 */

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { createServer, IncomingMessage, ServerResponse } from "node:http";
import { z } from "zod";

// ─── Config ──────────────────────────────────────────────────────────────────

// Java backend (apps/api-java) is the primary API; endpoints are wire-compatible with the old :3001.
const API_URL = process.env.API_URL ?? "http://localhost:8080";
const CHARACTER_LIMIT = 25_000;

// ─── Zod schemas (named so handler params are inferred) ───────────────────────

const SearchKitchensSchema = z.object({
  lat: z.number().min(-90).max(90).describe("Buyer latitude"),
  lng: z.number().min(-180).max(180).describe("Buyer longitude"),
  cuisine: z.string().optional().describe(
    "Optional cuisine filter: turkish | chinese | mexican | indian | italian | japanese | korean | vietnamese | lebanese | ethiopian | persian | greek | thai | other",
  ),
});

const GetMenuSchema = z.object({
  kitchenId: z.string().describe("Kitchen ID from search_kitchens"),
  date: z.string().optional().describe("ISO date YYYY-MM-DD, defaults to today"),
});

const CheckPortionsSchema = z.object({
  menuItemIds: z.array(z.string()).min(1).describe("menuItem IDs from get_menu items[].id"),
});

const CreateOrderSchema = z.object({
  kitchenId: z.string().describe("Kitchen ID"),
  menuDayId: z.string().describe("menuDay ID from get_menu"),
  items: z.array(
    z.object({
      menuItemId: z.string().describe("menuItem ID from get_menu items[].id"),
      qty: z.number().int().positive().describe("Quantity"),
    }),
  ).min(1),
  readySlot: z.string().describe("ISO datetime for the pickup/delivery window (from menuDay.readyWindows)"),
  fulfillment: z.enum(["pickup", "delivery"]),
  confirm: z.boolean().describe("false = priced summary only; true = place order after buyer confirms"),
});

const OrderIdSchema = z.object({
  orderId: z.string().describe("Order ID from create_order"),
});

// ─── HTTP helper ─────────────────────────────────────────────────────────────

async function apiCall(
  path: string,
  token: string,
  init?: RequestInit,
): Promise<{ isError?: boolean; content: Array<{ type: "text"; text: string }> }> {
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, {
      ...init,
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${token}`,
        ...(init?.headers ?? {}),
      },
    });
  } catch (e) {
    return {
      isError: true,
      content: [{
        type: "text",
        text: `Error: Could not reach CulturEats API (${e instanceof Error ? e.message : String(e)}). Is the API running at ${API_URL}?`,
      }],
    };
  }

  const body: unknown = await res.json().catch(() => ({}));
  const text = JSON.stringify(body, null, 2);

  if (!res.ok) {
    const hint =
      res.status === 401 ? " — check that your bearer token is valid"
      : res.status === 404 ? " — resource not found"
      : res.status === 409 ? " — inventory conflict; call check_portions to verify availability first"
      : "";
    return {
      isError: true,
      content: [{ type: "text", text: `Error ${res.status}${hint}:\n${text}` }],
    };
  }

  const trimmed =
    text.length > CHARACTER_LIMIT
      ? text.slice(0, CHARACTER_LIMIT) + "\n\n[Response truncated — use more specific filters]"
      : text;

  return { content: [{ type: "text", text: trimmed }] };
}

// ─── Server builder ───────────────────────────────────────────────────────────

export function buildServer(getToken: () => string): McpServer {
  const server = new McpServer({ name: "culture-eats-mcp-server", version: "0.1.0" });

  // ── Prompt: ordering assistant persona ─────────────────────────────────────

  server.registerPrompt(
    "ordering_assistant",
    {
      title: "CulturEats Ordering Assistant",
      description:
        "System prompt that configures an LLM as the CulturEats ordering assistant. " +
        "Inject as the system message before starting a buyer conversation.",
      argsSchema: {},
    },
    () => ({
      messages: [
        {
          role: "user" as const,
          content: {
            type: "text" as const,
            text: `You are the CulturEats ordering assistant. Help buyers discover and order home-cooked cultural food from local kitchens within 10 miles.

Rules:
1. Never invent dishes, prices, or kitchen names — all data must come from tool calls.
2. Before placing any order, call create_order with confirm=false to get a priced summary. Show the summary to the buyer and wait for explicit confirmation ("yes", "confirm", "place order") before calling create_order with confirm=true.
3. Always call check_portions before presenting an order summary to verify availability.
4. Only discuss food, kitchens, and orders on CulturEats.
5. Be concise and warm. Present search results as a numbered list: name, cuisine, distance, portions left today.`,
          },
        },
      ],
    }),
  );

  // ── Tool: search_kitchens ───────────────────────────────────────────────────

  server.registerTool(
    "search_kitchens",
    {
      title: "Search Kitchens",
      description: `Search for home kitchens within 10 miles of a location, sorted by distance.

Returns: JSON array of { id, name, cuisineTag, distanceMiles, ratingAvg, hygieneScore, portionsLeftToday }.
Use when: Buyer asks "what's near me", "find Turkish food", "any Korean kitchens?".`,
      inputSchema: SearchKitchensSchema,
      annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: true },
    },
    async ({ lat, lng, cuisine }: z.infer<typeof SearchKitchensSchema>) =>
      apiCall(
        `/kitchens/search?lat=${lat}&lng=${lng}${cuisine ? `&cuisine=${encodeURIComponent(cuisine)}` : ""}`,
        getToken(),
      ),
  );

  // ── Tool: get_menu ──────────────────────────────────────────────────────────

  server.registerTool(
    "get_menu",
    {
      title: "Get Kitchen Menu",
      description: `Get the published daily menu for a kitchen.

Returns: menuDay { id (menuDayId — required for create_order), date, readyWindows, items[]: { id (menuItemId), dish: { name, priceCents, dietaryTags }, portionsRemaining } }
Returns null if no published menu exists for that date.`,
      inputSchema: GetMenuSchema,
      annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: true },
    },
    async ({ kitchenId, date }: z.infer<typeof GetMenuSchema>) =>
      apiCall(
        `/kitchens/${encodeURIComponent(kitchenId)}/menu${date ? `?date=${encodeURIComponent(date)}` : ""}`,
        getToken(),
      ),
  );

  // ── Tool: check_portions ────────────────────────────────────────────────────

  server.registerTool(
    "check_portions",
    {
      title: "Check Portions",
      description: `Check live remaining portions for menu items. Always call before create_order to verify availability.

Returns: Array of { id, portionsRemaining, portionsTotal }.`,
      inputSchema: CheckPortionsSchema,
      annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: true },
    },
    async ({ menuItemIds }: z.infer<typeof CheckPortionsSchema>) =>
      apiCall(`/inventory/remaining`, getToken(), {
        method: "POST",
        body: JSON.stringify({ menuItemIds }),
      }),
  );

  // ── Tool: create_order ──────────────────────────────────────────────────────

  server.registerTool(
    "create_order",
    {
      title: "Create Order",
      description: `Create or confirm a food order — FR15 two-step guardrail.

STEP 1: confirm=false → returns priced summary { confirmed: false, summary: { items, totalCents, readySlot, fulfillment } }. Show to buyer, do NOT place order yet.
STEP 2: confirm=true (only after explicit buyer confirmation) → places order, returns { id, status, totalCents, readySlot }.

Error 409 PORTIONS_CONFLICT: call check_portions first to verify availability.`,
      inputSchema: CreateOrderSchema,
      annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: true },
    },
    async (draft: z.infer<typeof CreateOrderSchema>) =>
      apiCall(`/orders`, getToken(), {
        method: "POST",
        body: JSON.stringify(draft),
      }),
  );

  // ── Tool: get_order_status ──────────────────────────────────────────────────

  server.registerTool(
    "get_order_status",
    {
      title: "Get Order Status",
      description: `Get current status of a buyer's own order.

Status flow: pending → confirmed → accepted → preparing → ready → completed | cancelled | declined
Note: Pickup address only revealed once status is "accepted" or later (FR10).`,
      inputSchema: OrderIdSchema,
      annotations: { readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: true },
    },
    async ({ orderId }: z.infer<typeof OrderIdSchema>) =>
      apiCall(`/orders/${encodeURIComponent(orderId)}`, getToken()),
  );

  // ── Tool: cancel_order ──────────────────────────────────────────────────────

  server.registerTool(
    "cancel_order",
    {
      title: "Cancel Order",
      description: `Cancel a buyer's order. Inventory is automatically restored.

Only the placing buyer can cancel. Orders in "preparing", "ready", or terminal states may not be cancellable.`,
      inputSchema: OrderIdSchema,
      annotations: { readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: true },
    },
    async ({ orderId }: z.infer<typeof OrderIdSchema>) =>
      apiCall(`/orders/${encodeURIComponent(orderId)}/cancel`, getToken(), { method: "POST" }),
  );

  return server;
}

// ─── Transport entry points ───────────────────────────────────────────────────

async function runStdio(): Promise<void> {
  const token = process.env.CULTURE_EATS_TOKEN ?? "";
  if (!token) {
    process.stderr.write("Warning: CULTURE_EATS_TOKEN not set — tool calls will fail authentication.\n");
  }
  const server = buildServer(() => token);
  const transport = new StdioServerTransport();
  await server.connect(transport);
  process.stderr.write("CulturEats MCP server running via stdio\n");
}

function runHttp(): void {
  createServer(async (req: IncomingMessage, res: ServerResponse) => {
    const token = (req.headers.authorization ?? "").replace(/^Bearer\s+/i, "");
    const server = buildServer(() => token);
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on("close", () => transport.close());
    await server.connect(transport);
    await transport.handleRequest(req, res);
  }).listen(process.env.MCP_PORT ?? 3002, () =>
    process.stderr.write(`CulturEats MCP server on :${process.env.MCP_PORT ?? 3002}\n`),
  );
}

const mode = process.env.TRANSPORT ?? "stdio";
if (mode === "http") {
  runHttp();
} else {
  runStdio().catch((e: unknown) => {
    process.stderr.write(`Fatal: ${e}\n`);
    process.exit(1);
  });
}
