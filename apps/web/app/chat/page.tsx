"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import Link from "next/link";

interface Message {
  role: "user" | "assistant";
  content: string;
}

interface OrderSummary {
  confirmed: false;
  summary: {
    kitchenName?: string;
    items: { name: string; qty: number; priceCents: number }[];
    totalCents: number;
    readySlot: string;
    fulfillment: string;
    deliveryAddress?: string;
  };
  // raw draft stored so we can re-submit with confirm=true
  draft: Record<string, unknown>;
}

interface MenuCard {
  type: "menu";
  kitchenName: string;
  kitchenId: string;
  menuDayId: string;
  items: {
    menuItemId: string;
    name: string;
    description?: string | null;
    photo?: string | null;
    calories?: number | null;
    priceCents: number;
    portionsLeft?: number | null;
    dietaryTags?: string[] | null;
  }[];
}

// Java backend (apps/api-java); it proxies not-yet-ported endpoints to the legacy NestJS API.
const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

const SUGGESTIONS = [
  "Find Turkish food near me",
  "What's cooking in Lefkoşa today?",
  "I want to order sarma",
];

function cents(n: number) {
  return `$${(n / 100).toFixed(2)}`;
}

/** Geocodes the drop-off address (OpenStreetMap Nominatim) and embeds a marked map. */
function AddressMap({ address }: { address: string }) {
  const [coords, setCoords] = useState<{ lat: number; lon: number } | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let alive = true;
    setCoords(null);
    setFailed(false);
    fetch(
      `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(address)}`,
      { headers: { accept: "application/json" } },
    )
      .then((r) => r.json())
      .then((d) => {
        if (!alive) return;
        if (Array.isArray(d) && d[0]) setCoords({ lat: +d[0].lat, lon: +d[0].lon });
        else setFailed(true);
      })
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
    };
  }, [address]);

  if (failed) {
    return (
      <p style={{ fontSize: 13.5, color: "var(--text-3)", margin: "8px 0 0" }}>
        Map preview unavailable for this address.
      </p>
    );
  }
  if (!coords) {
    return (
      <div
        style={{
          height: 190,
          borderRadius: 14,
          background: "var(--surface-2)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          color: "var(--text-3)",
          fontSize: 14,
          marginTop: 12,
        }}
      >
        Locating address on the map
      </div>
    );
  }
  const d = 0.004;
  const bbox = `${coords.lon - d},${coords.lat - d},${coords.lon + d},${coords.lat + d}`;
  return (
    <iframe
      title="Delivery drop-off location"
      src={`https://www.openstreetmap.org/export/embed.html?bbox=${encodeURIComponent(bbox)}&layer=mapnik&marker=${coords.lat}%2C${coords.lon}`}
      style={{ width: "100%", height: 190, border: "1px solid var(--line)", borderRadius: 14, marginTop: 12 }}
      loading="lazy"
    />
  );
}

/** Inline markdown: [label](url) links and **bold**. No raw HTML ever reaches the DOM. */
function renderInline(text: string, keyBase: string) {
  const nodes: React.ReactNode[] = [];
  // Split out links first, then bold within the remaining text.
  const linkParts = text.split(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g);
  for (let i = 0; i < linkParts.length; i += 3) {
    const plain = linkParts[i];
    if (plain) {
      const boldParts = plain.split(/\*\*([^*]+)\*\*/g);
      boldParts.forEach((part, j) => {
        if (!part) return;
        if (j % 2 === 1) {
          nodes.push(<strong key={`${keyBase}-b${i}-${j}`}>{part}</strong>);
          return;
        }
        const italicParts = part.split(/\*([^*]+)\*/g);
        italicParts.forEach((seg, k) => {
          if (!seg) return;
          nodes.push(k % 2 === 1 ? <em key={`${keyBase}-i${i}-${j}-${k}`}>{seg}</em> : seg);
        });
      });
    }
    if (i + 2 < linkParts.length) {
      nodes.push(
        <a
          key={`${keyBase}-a${i}`}
          href={linkParts[i + 2]}
          target="_blank"
          rel="noreferrer"
          style={{ color: "inherit", fontWeight: 600, textDecoration: "underline" }}
        >
          {linkParts[i + 1]}
        </a>,
      );
    }
  }
  return nodes;
}

/** Assistant replies arrive as markdown; render bullets, links and bold instead of raw symbols. */
function renderRich(text: string) {
  return text.split("\n").map((line, i) => {
    const bullet = line.match(/^\s*[*-]\s+(.*)$/);
    const content = renderInline(bullet ? bullet[1] : line, `l${i}`);
    return (
      <span key={i} style={{ display: "block", paddingLeft: bullet ? 14 : 0 }}>
        {bullet && <span style={{ marginRight: 8 }}>&bull;</span>}
        {content}
      </span>
    );
  });
}

export default function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [pendingSummary, setPendingSummary] = useState<OrderSummary | null>(null);
  const [pendingMenu, setPendingMenu] = useState<MenuCard | null>(null);
  const [picked, setPicked] = useState<Record<string, number>>({});
  const [queued, setQueued] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const token = typeof window !== "undefined" ? localStorage.getItem("access_token") ?? "" : "";

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  // When the assistant finishes: send anything the user typed meanwhile, keep focus in the box.
  useEffect(() => {
    if (streaming) return;
    inputRef.current?.focus();
    if (queued) {
      const text = queued;
      setQueued(null);
      send(text);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [streaming]);

  function signOut() {
    localStorage.removeItem("access_token");
    localStorage.removeItem("refresh_token");
    window.location.href = "/login";
  }

  async function send(text: string) {
    if (!text.trim() || streaming) return;
    const next: Message[] = [...messages, { role: "user", content: text }];
    setMessages(next);
    setInput("");
    setStreaming(true);
    setPendingMenu(null);
    setPicked({});

    let assistantText = "";
    const addChunk = (delta: string) => {
      assistantText += delta;
      setMessages([...next, { role: "assistant", content: assistantText }]);
    };

    try {
      const res = await fetch(`${API}/chat/stream`, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
        body: JSON.stringify({ messages: next }),
      });

      if (res.status === 401 || res.status === 403) {
        window.location.href = "/login";
        return;
      }
      if (!res.ok) {
        setMessages([...next, { role: "assistant", content: `Something went wrong (HTTP ${res.status}). Please try again.` }]);
        return;
      }

      const reader = res.body!.getReader();
      const decoder = new TextDecoder();
      let buf = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buf += decoder.decode(value, { stream: true });
        const lines = buf.split("\n");
        buf = lines.pop() ?? "";
        for (const line of lines) {
          // NestJS wrote "data: {...}", Spring MVC writes "data:{...}" — accept both.
          if (!line.startsWith("data:")) continue;
          const payload = JSON.parse(line.slice(5).trim());
          if (payload.type === "text") addChunk(payload.delta);
          else if (payload.type === "done") break;
        }
      }

      // Structured card blocks embedded in assistant text (menu picker / order summary).
      const blockMatch = assistantText.match(/```json\n([\s\S]*?)\n```/);
      if (blockMatch) {
        try {
          const parsed = JSON.parse(blockMatch[1]);
          let handled = false;
          if (parsed.type === "menu" && Array.isArray(parsed.items)) {
            setPendingMenu(parsed);
            handled = true;
          } else if (parsed.confirmed === false && parsed.summary) {
            setPendingSummary(parsed);
            handled = true;
          }
          // The card renders the data; don't also show the raw JSON in the bubble.
          if (handled) assistantText = assistantText.replace(blockMatch[0], "").trim();
        } catch {}
      }

      setMessages([...next, { role: "assistant", content: assistantText }]);
    } catch {
      setMessages([
        ...next,
        {
          role: "assistant",
          content: assistantText
            ? assistantText + "\n\n[connection interrupted]"
            : "Connection error. Please try again.",
        },
      ]);
    } finally {
      setStreaming(false);
    }
  }

  async function confirmOrder() {
    if (!pendingSummary) return;
    const draft = { ...pendingSummary.draft, confirm: true };
    const res = await fetch(`${API}/orders`, {
      method: "POST",
      headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
      body: JSON.stringify(draft),
    });
    const body = await res.json();
    setPendingSummary(null);
    // POST /orders answers {confirmed: true, order: {...}} on success.
    const order = body.order ?? body;
    const tracking = order?.delivery?.trackingUrl
      ? `\nTrack your delivery: [tracking link](${order.delivery.trackingUrl})`
      : "";
    setMessages((prev) => [
      ...prev,
      {
        role: "assistant",
        content: res.ok ? `Order placed! ID: ${order.id}${tracking}` : `Error: ${body.message}`,
      },
    ]);
  }

  function addPickedToOrder() {
    if (!pendingMenu) return;
    const parts = pendingMenu.items
      .filter((it) => (picked[it.menuItemId] ?? 0) > 0)
      .map((it) => `${picked[it.menuItemId]} x ${it.name}`);
    if (parts.length === 0) return;
    const text = `I'd like ${parts.join(", ")} from ${pendingMenu.kitchenName}.`;
    setPendingMenu(null);
    setPicked({});
    send(text);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!input.trim()) return;
    if (streaming) {
      // Queue it; the effect above fires it as soon as the assistant finishes.
      setQueued(input.trim());
      setInput("");
      return;
    }
    send(input);
  }

  const lastMessage = messages[messages.length - 1];
  const showTyping = streaming && (!lastMessage || lastMessage.role === "user" || !lastMessage.content);

  return (
    <main
      style={{
        display: "flex",
        flexDirection: "column",
        minHeight: "100dvh",
        maxWidth: 760,
        margin: "0 auto",
        padding: "14px 16px 0",
        position: "relative",
      }}
    >
      <div className="hero-glow" aria-hidden="true" style={{ opacity: 0.6 }} />

      <header className="island-nav" style={{ maxWidth: 480, margin: "0 auto", width: "100%" }}>
        <Link href="/" style={{ fontWeight: 700, fontSize: 16, letterSpacing: "-0.02em" }}>
          Nanas&rsquo; Kitchens
        </Link>
        <button
          onClick={signOut}
          className="btn btn-ghost"
          style={{ padding: "7px 16px", fontSize: 13.5 }}
        >
          Sign out
        </button>
      </header>

      <div role="log" aria-live="polite" style={{ flex: 1, overflowY: "auto", padding: "24px 2px" }}>
        {messages.length === 0 && (
          <div style={{ textAlign: "center", marginTop: "13vh" }}>
            <div className="halo-orb stagger" style={{ "--i": 0 } as React.CSSProperties}>
              N
            </div>
            <h1
              className="stagger"
              style={
                {
                  "--i": 1,
                  fontSize: "clamp(26px, 4vw, 34px)",
                  fontWeight: 700,
                  letterSpacing: "-0.03em",
                  margin: "0 0 10px",
                } as React.CSSProperties
              }
            >
              What are you <span className="hero-em">craving</span> today?
            </h1>
            <p
              className="stagger"
              style={{ "--i": 2, color: "var(--text-2)", fontSize: 15.5, margin: "0 0 32px" } as React.CSSProperties}
            >
              Find kitchens, browse menus, and order in one conversation.
            </p>
            <div style={{ display: "flex", gap: 10, justifyContent: "center", flexWrap: "wrap" }}>
              {SUGGESTIONS.map((s, i) => (
                <button
                  key={s}
                  className="chip stagger"
                  style={{ "--i": 3 + i } as React.CSSProperties}
                  onClick={() => send(s)}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((m, i) => (
          <div
            key={i}
            className="fade-up"
            style={{
              marginBottom: 16,
              display: "flex",
              gap: 10,
              justifyContent: m.role === "user" ? "flex-end" : "flex-start",
            }}
          >
            {m.role === "assistant" && <div className="avatar-orb">N</div>}
            <div className={`bubble ${m.role === "user" ? "bubble-user" : "bubble-assistant"}`}>
              {renderRich(m.content)}
            </div>
          </div>
        ))}

        {showTyping && (
          <div className="fade-up" style={{ display: "flex", gap: 10, justifyContent: "flex-start", marginBottom: 16 }}>
            <div className="avatar-orb">N</div>
            <div
              className="bubble bubble-assistant"
              style={{ display: "flex", gap: 5, alignItems: "center", padding: "15px 18px" }}
              aria-label="Assistant is typing"
            >
              <span className="typing-dot" />
              <span className="typing-dot" />
              <span className="typing-dot" />
            </div>
          </div>
        )}

        {/* Dish picker card: photos, ingredients, calories, quantity steppers */}
        {pendingMenu && (
          <div className="fade-up shell" style={{ margin: "14px 0" }}>
            <div className="shell-core" style={{ padding: "18px 20px" }}>
              <h2 style={{ margin: "0 0 2px", fontSize: 16, fontWeight: 700 }}>
                {pendingMenu.kitchenName}
              </h2>
              <p style={{ margin: "0 0 6px", color: "var(--text-2)", fontSize: 13.5 }}>
                Pick your dishes, then add them to the order.
              </p>
              {pendingMenu.items.map((it) => {
                const q = picked[it.menuItemId] ?? 0;
                const max = it.portionsLeft ?? 99;
                return (
                  <div key={it.menuItemId} className="dish-row">
                    {it.photo ? (
                      <img src={it.photo} alt={it.name} className="dish-photo" />
                    ) : (
                      <div className="dish-photo" />
                    )}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 15 }}>{it.name}</div>
                      {it.description && (
                        <div
                          style={{
                            fontSize: 13,
                            color: "var(--text-2)",
                            marginTop: 2,
                            lineHeight: 1.45,
                          }}
                        >
                          {it.description}
                        </div>
                      )}
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: 8,
                          marginTop: 6,
                          flexWrap: "wrap",
                        }}
                      >
                        <span style={{ fontWeight: 700, fontSize: 14 }}>{cents(it.priceCents)}</span>
                        {typeof it.calories === "number" && (
                          <span className="kcal">~{it.calories} kcal</span>
                        )}
                        {typeof it.portionsLeft === "number" && (
                          <span style={{ fontSize: 12.5, color: "var(--text-3)" }}>
                            {it.portionsLeft} left
                          </span>
                        )}
                        {(it.dietaryTags ?? []).map((tag) => (
                          <span key={tag} className="kcal" style={{ textTransform: "capitalize" }}>
                            {tag}
                          </span>
                        ))}
                      </div>
                    </div>
                    <div className="stepper" aria-label={`Quantity for ${it.name}`}>
                      <button
                        type="button"
                        disabled={q === 0}
                        onClick={() => setPicked({ ...picked, [it.menuItemId]: Math.max(0, q - 1) })}
                        aria-label={`Remove one ${it.name}`}
                      >
                        &minus;
                      </button>
                      <span className="qty">{q}</span>
                      <button
                        type="button"
                        disabled={q >= max}
                        onClick={() => setPicked({ ...picked, [it.menuItemId]: q + 1 })}
                        aria-label={`Add one ${it.name}`}
                      >
                        +
                      </button>
                    </div>
                  </div>
                );
              })}
              {(() => {
                const count = Object.values(picked).reduce((a, b) => a + b, 0);
                const total = pendingMenu.items.reduce(
                  (sum, it) => sum + (picked[it.menuItemId] ?? 0) * it.priceCents,
                  0,
                );
                return (
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      marginTop: 14,
                      gap: 12,
                    }}
                  >
                    <span style={{ fontSize: 14.5, color: "var(--text-2)" }}>
                      {count > 0 ? (
                        <>
                          {count} item{count === 1 ? "" : "s"},{" "}
                          <strong style={{ color: "var(--text)" }}>{cents(total)}</strong>
                        </>
                      ) : (
                        "Nothing picked yet"
                      )}
                    </span>
                    <button
                      onClick={addPickedToOrder}
                      disabled={count === 0 || streaming}
                      className="btn btn-primary"
                      style={{ padding: "10px 22px" }}
                    >
                      Add to order
                    </button>
                  </div>
                );
              })()}
            </div>
          </div>
        )}

        {/* Order confirmation card (FR15) */}
        {pendingSummary && (
          <div role="dialog" aria-labelledby="summary-heading" className="fade-up shell" style={{ margin: "14px 0" }}>
            <div className="shell-core" style={{ padding: 20 }}>
            <h2 id="summary-heading" style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700 }}>
              Order summary
            </h2>
            {pendingSummary.summary.kitchenName && (
              <p style={{ margin: "0 0 12px", color: "var(--text-2)", fontSize: 14 }}>
                {pendingSummary.summary.kitchenName}
              </p>
            )}
            <div style={{ borderTop: "1px solid var(--line)", padding: "12px 0", margin: "8px 0" }}>
              {pendingSummary.summary.items.map((it, i) => (
                <div
                  key={i}
                  style={{ display: "flex", justifyContent: "space-between", fontSize: 15, padding: "4px 0" }}
                >
                  <span>
                    {it.qty} &times; {it.name}
                  </span>
                  <span style={{ fontWeight: 600 }}>{cents(it.priceCents * it.qty)}</span>
                </div>
              ))}
            </div>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: 15,
                fontWeight: 700,
                padding: "4px 0 2px",
              }}
            >
              <span>Total</span>
              <span>{cents(pendingSummary.summary.totalCents)}</span>
            </div>
            <p style={{ margin: "10px 0 2px", fontSize: 14, color: "var(--text-2)" }}>
              Ready {new Date(pendingSummary.summary.readySlot).toLocaleString()}
              {", "}
              {pendingSummary.summary.fulfillment}
            </p>
            {pendingSummary.summary.deliveryAddress && (
              <>
                <p style={{ margin: "8px 0 0", fontSize: 14.5 }}>
                  <strong>Deliver to:</strong> {pendingSummary.summary.deliveryAddress}
                </p>
                <AddressMap address={pendingSummary.summary.deliveryAddress} />
              </>
            )}
            <p style={{ margin: "16px 0 0", fontSize: 15, fontWeight: 600 }}>
              Do you confirm this order?
            </p>
            <div style={{ display: "flex", gap: 10, marginTop: 12 }}>
              <button onClick={confirmOrder} className="btn btn-primary" style={{ padding: "10px 22px" }}>
                Confirm order
              </button>
              <button onClick={() => setPendingSummary(null)} className="btn btn-ghost" style={{ padding: "9px 18px" }}>
                Cancel
              </button>
            </div>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      <div style={{ marginTop: "auto" }}>
        {queued && (
          <p
            aria-live="polite"
            style={{
              fontSize: 13,
              color: "var(--text-3)",
              margin: "0 0 6px",
              paddingLeft: 22,
            }}
          >
            Will send when the assistant finishes: &ldquo;{queued}&rdquo;
          </p>
        )}
        <form onSubmit={onSubmit} className="chat-dock" style={{ marginBottom: 14 }}>
          <input
            ref={inputRef}
            aria-label="Message"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            autoFocus
            placeholder="Ask for a dish, a cuisine, or a kitchen"
          />
          <button type="submit" disabled={!input.trim()} aria-label="Send" className="send-orb">
            &#8599;
          </button>
        </form>
      </div>
    </main>
  );
}
