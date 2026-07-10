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
  };
  // raw draft stored so we can re-submit with confirm=true
  draft: Record<string, unknown>;
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

      // Try to parse a confirmation summary embedded in assistant text
      const summaryMatch = assistantText.match(/```json\n([\s\S]*?)\n```/);
      if (summaryMatch) {
        try {
          const parsed = JSON.parse(summaryMatch[1]);
          if (parsed.confirmed === false && parsed.summary) setPendingSummary(parsed);
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
    setMessages((prev) => [
      ...prev,
      { role: "assistant", content: res.ok ? `Order placed! ID: ${body.id}` : `Error: ${body.message}` },
    ]);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    send(input);
  }

  const lastMessage = messages[messages.length - 1];
  const showTyping = streaming && (!lastMessage || lastMessage.role === "user" || !lastMessage.content);

  return (
    <main
      style={{
        display: "flex",
        flexDirection: "column",
        height: "100dvh",
        maxWidth: 720,
        margin: "0 auto",
        padding: "0 16px",
      }}
    >
      <header
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "18px 4px 14px",
          borderBottom: "1px solid var(--line)",
        }}
      >
        <Link href="/" style={{ fontWeight: 700, fontSize: 16, letterSpacing: "-0.02em" }}>
          Nanas&rsquo; Kitchens
        </Link>
        <button
          onClick={signOut}
          style={{
            background: "transparent",
            border: "none",
            color: "var(--text-2)",
            fontSize: 14,
            padding: "4px 2px",
          }}
        >
          Sign out
        </button>
      </header>

      <div role="log" aria-live="polite" style={{ flex: 1, overflowY: "auto", padding: "24px 2px" }}>
        {messages.length === 0 && (
          <div className="fade-up" style={{ textAlign: "center", marginTop: "14vh" }}>
            <div
              className="monogram"
              style={{ width: 56, height: 56, borderRadius: 16, fontSize: 22, margin: "0 auto 20px" }}
            >
              N
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-0.02em", margin: "0 0 8px" }}>
              What are you craving?
            </h1>
            <p style={{ color: "var(--text-2)", fontSize: 15, margin: "0 0 28px" }}>
              Find kitchens, browse menus, and order in one conversation.
            </p>
            <div style={{ display: "flex", gap: 10, justifyContent: "center", flexWrap: "wrap" }}>
              {SUGGESTIONS.map((s) => (
                <button key={s} className="chip" onClick={() => send(s)}>
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
              marginBottom: 14,
              display: "flex",
              justifyContent: m.role === "user" ? "flex-end" : "flex-start",
            }}
          >
            <div className={`bubble ${m.role === "user" ? "bubble-user" : "bubble-assistant"}`}>
              {m.content}
            </div>
          </div>
        ))}

        {showTyping && (
          <div className="fade-up" style={{ display: "flex", justifyContent: "flex-start", marginBottom: 14 }}>
            <div
              className="bubble bubble-assistant"
              style={{ display: "flex", gap: 5, alignItems: "center", padding: "14px 18px" }}
              aria-label="Assistant is typing"
            >
              <span className="typing-dot" />
              <span className="typing-dot" />
              <span className="typing-dot" />
            </div>
          </div>
        )}

        {/* Order confirmation card (FR15) */}
        {pendingSummary && (
          <div
            role="dialog"
            aria-labelledby="summary-heading"
            className="fade-up"
            style={{
              border: "1px solid var(--line)",
              borderRadius: "var(--radius)",
              padding: 20,
              margin: "14px 0",
              background: "var(--surface)",
              boxShadow: "var(--shadow)",
            }}
          >
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
            <div style={{ display: "flex", gap: 10, marginTop: 16 }}>
              <button onClick={confirmOrder} className="btn btn-primary" style={{ padding: "10px 22px" }}>
                Confirm order
              </button>
              <button onClick={() => setPendingSummary(null)} className="btn btn-ghost" style={{ padding: "9px 18px" }}>
                Cancel
              </button>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      <form onSubmit={onSubmit} style={{ display: "flex", gap: 10, padding: "12px 0 18px" }}>
        <input
          aria-label="Message"
          className="input"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          disabled={streaming}
          placeholder={streaming ? "Assistant is typing" : "Ask for a dish, a cuisine, or a kitchen"}
          style={{ borderRadius: 999, padding: "13px 20px" }}
        />
        <button
          type="submit"
          disabled={streaming || !input.trim()}
          aria-label="Send"
          className="btn btn-primary"
          style={{ padding: "12px 24px" }}
        >
          Send
        </button>
      </form>
    </main>
  );
}
