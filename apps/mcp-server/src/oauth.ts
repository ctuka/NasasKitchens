/**
 * Story 5.4 (FR14) — OAuth 2.1 for external MCP clients.
 *
 * The MCP server doubles as the authorization server; the platform API stays the only
 * credential verifier (/auth/login, /auth/refresh). Flow per the MCP auth spec:
 *   1. Client hits the MCP endpoint without a token → 401 + WWW-Authenticate pointing at
 *      /.well-known/oauth-protected-resource.
 *   2. Client discovers the authorization server metadata, registers dynamically
 *      (public client, PKCE S256 required), and sends the buyer to /authorize.
 *   3. /authorize shows a login form; credentials are verified against the platform API
 *      and NEVER stored here — success mints a single-use 5-minute authorization code.
 *   4. /token exchanges code + PKCE verifier for the platform JWT pair (access_token =
 *      platform access token, so resource calls forward it unchanged; refresh_token
 *      grant delegates to /auth/refresh).
 *
 * Registrations and pending codes are in-memory — fine for dev/demo, a shared store is
 * needed before multi-instance deployment.
 */

import { createHash, randomBytes } from "node:crypto";
import { IncomingMessage, ServerResponse } from "node:http";

const CODE_TTL_MS = 5 * 60 * 1000;
const ACCESS_TOKEN_TTL_S = 900; // platform access tokens live 15 minutes

interface RegisteredClient {
  clientId: string;
  clientName: string;
  redirectUris: string[];
}

interface PendingCode {
  clientId: string;
  redirectUri: string;
  codeChallenge: string;
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}

const clients = new Map<string, RegisteredClient>();
const codes = new Map<string, PendingCode>();

const b64url = (buf: Buffer) => buf.toString("base64url");
const s256 = (verifier: string) => b64url(createHash("sha256").update(verifier).digest());

// ─── Small HTTP helpers ───────────────────────────────────────────────────────

export function setCors(res: ServerResponse): void {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Mcp-Session-Id, Mcp-Protocol-Version");
  res.setHeader("Access-Control-Expose-Headers", "Mcp-Session-Id, WWW-Authenticate");
}

function json(res: ServerResponse, status: number, body: unknown): void {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(JSON.stringify(body));
}

function html(res: ServerResponse, status: number, body: string): void {
  res.writeHead(status, { "content-type": "text/html; charset=utf-8" });
  res.end(body);
}

async function readBody(req: IncomingMessage): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  return Buffer.concat(chunks).toString("utf8");
}

// ─── Login form (server-rendered, no client secrets involved) ─────────────────

function loginForm(params: URLSearchParams, clientName: string, error?: string): string {
  const esc = (s: string) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
  const hidden = ["client_id", "redirect_uri", "state", "code_challenge", "code_challenge_method"]
    .map((k) => `<input type="hidden" name="${k}" value="${esc(params.get(k) ?? "")}">`)
    .join("\n      ");
  return `<!doctype html>
<html><head><meta charset="utf-8"><title>Nanas' Kitchens — Authorize</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  body { font-family: system-ui, sans-serif; background: #faf3e4; color: #24281f; display: grid; place-items: center; min-height: 100vh; margin: 0; }
  .card { background: #fff; border: 1px solid #e5decb; border-radius: 16px; padding: 32px; width: min(360px, 90vw); }
  h1 { font-size: 20px; margin: 0 0 4px; color: #2e4a2e; }
  p { font-size: 14px; color: #6b7280; margin: 0 0 20px; }
  label { font-size: 14px; display: block; margin-bottom: 12px; }
  input[type=email], input[type=password] { width: 100%; box-sizing: border-box; padding: 10px 12px; margin-top: 4px; border: 1px solid #e5decb; border-radius: 10px; font-size: 15px; }
  button { width: 100%; padding: 12px; background: #e8720c; color: #fff; border: none; border-radius: 10px; font-size: 16px; font-weight: 600; cursor: pointer; }
  .error { background: #fef2f2; color: #b91c1c; border: 1px solid #fecaca; border-radius: 10px; padding: 10px 14px; font-size: 14px; margin-bottom: 16px; }
</style></head>
<body>
  <div class="card">
    <h1>Nanas&rsquo; Kitchens</h1>
    <p><strong>${esc(clientName)}</strong> wants to order food on your behalf.</p>
    ${error ? `<div class="error">${esc(error)}</div>` : ""}
    <form method="post" action="/authorize">
      ${hidden}
      <label>Email <input type="email" name="email" required autofocus></label>
      <label>Password <input type="password" name="password" required></label>
      <button type="submit">Log in &amp; authorize</button>
    </form>
  </div>
</body></html>`;
}

// ─── Route handler ────────────────────────────────────────────────────────────

/** Handles OAuth endpoints; returns false when the path belongs to the MCP transport. */
export async function handleOAuth(
  req: IncomingMessage,
  res: ServerResponse,
  baseUrl: string,
  apiUrl: string,
): Promise<boolean> {
  const url = new URL(req.url ?? "/", baseUrl);

  // RFC 9728 — points 401'd MCP clients at the authorization server.
  if (url.pathname === "/.well-known/oauth-protected-resource") {
    json(res, 200, {
      resource: baseUrl,
      authorization_servers: [baseUrl],
      bearer_methods_supported: ["header"],
    });
    return true;
  }

  // RFC 8414 authorization server metadata.
  if (url.pathname.startsWith("/.well-known/oauth-authorization-server")) {
    json(res, 200, {
      issuer: baseUrl,
      authorization_endpoint: `${baseUrl}/authorize`,
      token_endpoint: `${baseUrl}/token`,
      registration_endpoint: `${baseUrl}/register`,
      response_types_supported: ["code"],
      grant_types_supported: ["authorization_code", "refresh_token"],
      code_challenge_methods_supported: ["S256"],
      token_endpoint_auth_methods_supported: ["none"],
    });
    return true;
  }

  // RFC 7591 dynamic client registration (public clients only).
  if (url.pathname === "/register" && req.method === "POST") {
    let body: { redirect_uris?: string[]; client_name?: string };
    try {
      body = JSON.parse(await readBody(req));
    } catch {
      json(res, 400, { error: "invalid_client_metadata" });
      return true;
    }
    if (!Array.isArray(body.redirect_uris) || body.redirect_uris.length === 0) {
      json(res, 400, { error: "invalid_redirect_uri" });
      return true;
    }
    const client: RegisteredClient = {
      clientId: randomBytes(16).toString("hex"),
      clientName: body.client_name ?? "MCP client",
      redirectUris: body.redirect_uris,
    };
    clients.set(client.clientId, client);
    json(res, 201, {
      client_id: client.clientId,
      client_name: client.clientName,
      redirect_uris: client.redirectUris,
      token_endpoint_auth_method: "none",
      grant_types: ["authorization_code", "refresh_token"],
      response_types: ["code"],
    });
    return true;
  }

  if (url.pathname === "/authorize" && req.method === "GET") {
    const client = clients.get(url.searchParams.get("client_id") ?? "");
    const redirectUri = url.searchParams.get("redirect_uri") ?? "";
    if (!client || !client.redirectUris.includes(redirectUri)) {
      html(res, 400, "<p>Unknown client or redirect_uri — register first.</p>");
      return true;
    }
    if (url.searchParams.get("code_challenge_method") !== "S256" || !url.searchParams.get("code_challenge")) {
      html(res, 400, "<p>PKCE S256 is required.</p>");
      return true;
    }
    html(res, 200, loginForm(url.searchParams, client.clientName));
    return true;
  }

  if (url.pathname === "/authorize" && req.method === "POST") {
    const form = new URLSearchParams(await readBody(req));
    const client = clients.get(form.get("client_id") ?? "");
    const redirectUri = form.get("redirect_uri") ?? "";
    if (!client || !client.redirectUris.includes(redirectUri)) {
      html(res, 400, "<p>Unknown client or redirect_uri.</p>");
      return true;
    }
    // Verify credentials against the platform — the only identity source (NFR3 spirit).
    const login = await fetch(`${apiUrl}/auth/login`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ email: form.get("email"), password: form.get("password") }),
    }).catch(() => null);
    if (!login || !login.ok) {
      html(res, 401, loginForm(form, client.clientName, "Wrong email or password — try again."));
      return true;
    }
    const tokens = (await login.json()) as { accessToken: string; refreshToken: string };
    const code = randomBytes(24).toString("base64url");
    codes.set(code, {
      clientId: client.clientId,
      redirectUri,
      codeChallenge: form.get("code_challenge") ?? "",
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      expiresAt: Date.now() + CODE_TTL_MS,
    });
    const target = new URL(redirectUri);
    target.searchParams.set("code", code);
    const state = form.get("state");
    if (state) target.searchParams.set("state", state);
    res.writeHead(302, { location: target.toString() });
    res.end();
    return true;
  }

  if (url.pathname === "/token" && req.method === "POST") {
    const form = new URLSearchParams(await readBody(req));
    const grant = form.get("grant_type");

    if (grant === "authorization_code") {
      const pending = codes.get(form.get("code") ?? "");
      codes.delete(form.get("code") ?? ""); // single use, success or not
      if (
        !pending ||
        pending.expiresAt < Date.now() ||
        pending.clientId !== form.get("client_id") ||
        (form.get("redirect_uri") ?? "") !== pending.redirectUri ||
        s256(form.get("code_verifier") ?? "") !== pending.codeChallenge
      ) {
        json(res, 400, { error: "invalid_grant" });
        return true;
      }
      json(res, 200, {
        access_token: pending.accessToken,
        token_type: "Bearer",
        expires_in: ACCESS_TOKEN_TTL_S,
        refresh_token: pending.refreshToken,
      });
      return true;
    }

    if (grant === "refresh_token") {
      const refresh = await fetch(`${apiUrl}/auth/refresh`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ refreshToken: form.get("refresh_token") }),
      }).catch(() => null);
      if (!refresh || !refresh.ok) {
        json(res, 400, { error: "invalid_grant" });
        return true;
      }
      const tokens = (await refresh.json()) as { accessToken: string; refreshToken: string };
      json(res, 200, {
        access_token: tokens.accessToken,
        token_type: "Bearer",
        expires_in: ACCESS_TOKEN_TTL_S,
        refresh_token: tokens.refreshToken,
      });
      return true;
    }

    json(res, 400, { error: "unsupported_grant_type" });
    return true;
  }

  return false; // not an OAuth path — hand over to the MCP transport
}
