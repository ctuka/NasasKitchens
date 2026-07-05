"use client";

import { FormEvent, useEffect, useRef, useState } from "react";

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
  };
  // raw draft stored so we can re-submit with confirm=true
  draft: Record<string, unknown>;
}

// Java backend (apps/api-java); it proxies not-yet-ported endpoints to the legacy NestJS API.
const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

function cents(n: number) {
  return `$${(n / 100).toFixed(2)}`;
}

export default function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [pendingSummary, setPendingSummary] = useState<OrderSummary | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const token = typeof window !== "undefined" ? localStorage.getItem("access_token") ?? "" : "";

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  async function send(text: string) {
    if (!text.trim() || streaming) return;
    const next: Message[] = [...messages, { role: "user", content: text }];
    setMessages(next);
    setInput("");
    setStreaming(true);

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
          if (!line.startsWith("data: ")) continue;
          const payload = JSON.parse(line.slice(6));
          if (payload.type === "text") addChunk(payload.delta);
          else if (payload.type === "done") break;
        }
      }

      // Try to parse a confirmation summary embedded in assistant text
      const summaryMatch = assistantText.match(/```json\n([\s\S]*?)\n```/);
      if (summaryMatch) {
        try {
          const parsed = JSON.parse(summaryMatch[1]);
          if (parsed.confirmed === false && parsed.summary) setPendingSummary(parsed);
        } catch {}
      }

      setMessages([...next, { role: "assistant", content: assistantText }]);
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
    setMessages((prev) => [
      ...prev,
      { role: "assistant", content: res.ok ? `Order placed! ID: ${body.id}` : `Error: ${body.message}` },
    ]);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    send(input);
  }

  return (
    <main style={{ display: "flex", flexDirection: "column", height: "100dvh", maxWidth: 680, margin: "0 auto" }}>
      <header style={{ padding: "12px 16px", borderBottom: "1px solid #e5e7eb", fontWeight: 700, fontSize: 18 }}>
        Nanas' Kitchens — Order Assistant
      </header>

      <div role="log" aria-live="polite" style={{ flex: 1, overflowY: "auto", padding: "16px" }}>
        {messages.length === 0 && (
          <p style={{ color: "#6b7280", textAlign: "center", marginTop: 40 }}>
            Ask me to find food near you, browse a menu, or place an order.
          </p>
        )}
        {messages.map((m, i) => (
          <div
            key={i}
            style={{
              marginBottom: 12,
              display: "flex",
              justifyContent: m.role === "user" ? "flex-end" : "flex-start",
            }}
          >
            <div
              style={{
                maxWidth: "80%",
                padding: "10px 14px",
                borderRadius: 16,
                background: m.role === "user" ? "#2563eb" : "#f3f4f6",
                color: m.role === "user" ? "#fff" : "#111",
                whiteSpace: "pre-wrap",
                fontSize: 15,
              }}
            >
              {m.content}
            </div>
          </div>
        ))}

        {/* Order confirmation card (FR15) */}
        {pendingSummary && (
          <div
            role="dialog"
            aria-labelledby="summary-heading"
            style={{
              border: "2px solid #2563eb",
              borderRadius: 12,
              padding: 16,
              margin: "12px 0",
              background: "#eff6ff",
            }}
          >
            <h2 id="summary-heading" style={{ margin: "0 0 8px", fontSize: 16 }}>
              Order Summary — please confirm
            </h2>
            {pendingSummary.summary.kitchenName && (
              <p style={{ margin: "0 0 4px", fontWeight: 600 }}>{pendingSummary.summary.kitchenName}</p>
            )}
            <ul style={{ margin: "8px 0", paddingLeft: 20 }}>
              {pendingSummary.summary.items.map((it, i) => (
                <li key={i}>
                  {it.qty}× {it.name} — {cents(it.priceCents * it.qty)}
                </li>
              ))}
            </ul>
            <p style={{ margin: "4px 0" }}>
              <strong>Total:</strong> {cents(pendingSummary.summary.totalCents)}
            </p>
            <p style={{ margin: "4px 0" }}>
              <strong>Ready:</strong> {new Date(pendingSummary.summary.readySlot).toLocaleString()}
            </p>
            <p style={{ margin: "4px 0 12px" }}>
              <strong>Fulfillment:</strong> {pendingSummary.summary.fulfillment}
            </p>
            <div style={{ display: "flex", gap: 8 }}>
              <button
                onClick={confirmOrder}
                style={{
                  background: "#2563eb",
                  color: "#fff",
                  border: "none",
                  borderRadius: 8,
                  padding: "8px 20px",
                  cursor: "pointer",
                  fontWeight: 600,
                }}
              >
                Confirm order
              </button>
              <button
                onClick={() => setPendingSummary(null)}
                style={{
                  background: "transparent",
                  border: "1px solid #6b7280",
                  borderRadius: 8,
                  padding: "8px 16px",
                  cursor: "pointer",
                }}
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      <form
        onSubmit={onSubmit}
        style={{ padding: "12px 16px", borderTop: "1px solid #e5e7eb", display: "flex", gap: 8 }}
      >
        <input
          aria-label="Message"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={streaming}
          placeholder={streaming ? "Assistant is typing…" : "Type a message…"}
          style={{
            flex: 1,
            padding: "10px 14px",
            borderRadius: 24,
            border: "1px solid #d1d5db",
            fontSize: 15,
            outline: "none",
          }}
        />
        <button
          type="submit"
          disabled={streaming || !input.trim()}
          aria-label="Send"
          style={{
            background: "#2563eb",
            color: "#fff",
            border: "none",
            borderRadius: 24,
            padding: "10px 20px",
            cursor: "pointer",
            fontWeight: 600,
            opacity: streaming || !input.trim() ? 0.5 : 1,
          }}
        >
          Send
        </button>
      </form>
    </main>
  );
}
