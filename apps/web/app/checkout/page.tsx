"use client";

/** Cart & Checkout (front-end-spec.md, FR7/FR8/FR10/FR21, Story 3.3). Ready-time slots come
 * from the kitchen's published windows; the priced summary and the placement both go through
 * POST /orders (confirm=false then true — the same FR15 guardrail the agent uses). Card payment
 * (Stripe PaymentSheet) is Story 3.4; until then "Place order" confirms directly. */
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { API, apiFetch, getSession } from "../../lib/api";
import { Cart, clearCart, getCart, money, setQty, subscribeCart } from "../../lib/cart";

interface ReadyWindow {
  start: string;
  end: string;
  slotMinutes: number;
}

interface MenuDay {
  id: string;
  date: string;
  readyWindows: ReadyWindow[];
}

interface PricedSummary {
  totalCents: number;
}

type Fulfillment = "pickup" | "delivery";

/** Expands published windows into concrete "YYYY-MM-DDTHH:MM" slots for the menu day. */
function buildSlots(date: string, windows: ReadyWindow[]): { iso: string; label: string }[] {
  const slots: { iso: string; label: string }[] = [];
  for (const w of windows ?? []) {
    const [sh, sm] = w.start.split(":").map(Number);
    const [eh, em] = w.end.split(":").map(Number);
    const step = w.slotMinutes || 30;
    for (let mins = sh * 60 + sm; mins < eh * 60 + em; mins += step) {
      const hh = String(Math.floor(mins / 60)).padStart(2, "0");
      const mm = String(mins % 60).padStart(2, "0");
      slots.push({ iso: `${date}T${hh}:${mm}`, label: `${hh}:${mm}` });
    }
  }
  return slots;
}

export default function CheckoutPage() {
  const router = useRouter();
  const [cart, setCart] = useState<Cart | null>(null);
  const [ready, setReady] = useState(false); // cart hydrated (avoids SSR/empty flash)
  const [menu, setMenu] = useState<MenuDay | null | undefined>(undefined);
  const [slot, setSlot] = useState<string | null>(null);
  const [fulfillment, setFulfillment] = useState<Fulfillment>("pickup");
  const [summary, setSummary] = useState<PricedSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [busy, setBusy] = useState(false);

  const session = typeof window !== "undefined" ? getSession() : null;

  useEffect(() => {
    setCart(getCart());
    setReady(true);
    return subscribeCart(() => setCart(getCart()));
  }, []);

  useEffect(() => {
    if (!cart) return;
    let cancelled = false;
    fetch(`${API}/kitchens/${cart.kitchenId}/menu`)
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((body: MenuDay | null) => !cancelled && setMenu(body))
      .catch(() => !cancelled && setMenu(null));
    return () => {
      cancelled = true;
    };
  }, [cart?.kitchenId]);

  const slots = useMemo(
    () => (menu ? buildSlots(menu.date, menu.readyWindows) : []),
    [menu],
  );

  const subtotal = cart ? cart.lines.reduce((n, l) => n + l.priceCents * l.qty, 0) : 0;

  function orderBody(confirm: boolean) {
    return {
      kitchenId: cart!.kitchenId,
      menuDayId: cart!.menuDayId,
      items: cart!.lines.map((l) => ({ menuItemId: l.menuItemId, qty: l.qty })),
      readySlot: slot,
      fulfillment,
      confirm,
    };
  }

  async function review() {
    setError(null);
    setConflict(false);
    setBusy(true);
    try {
      const res = await apiFetch("/orders", { method: "POST", body: JSON.stringify(orderBody(false)) });
      const body = await res.json();
      if (!res.ok) {
        setError(body.message ?? "Could not price this order.");
        return;
      }
      setSummary(body.summary as PricedSummary);
    } catch {
      setError("Network error — is the API running?");
    } finally {
      setBusy(false);
    }
  }

  async function place() {
    setError(null);
    setBusy(true);
    try {
      const res = await apiFetch("/orders", { method: "POST", body: JSON.stringify(orderBody(true)) });
      const body = await res.json();
      if (res.status === 409 && String(body.message).startsWith("PORTIONS_CONFLICT")) {
        setConflict(true);
        setSummary(null);
        return;
      }
      if (!res.ok) {
        setError(body.message ?? "Could not place the order.");
        return;
      }
      if (body.requiresPayment) {
        // Server runs the real Stripe provider (Story 3.4) but the web payment sheet
        // isn't built yet; the unpaid pending order auto-releases server-side.
        setError("Card payment is required by this server, but the web payment form isn't available yet.");
        return;
      }
      clearCart();
      router.push(`/orders/${body.order.id}`);
    } catch {
      setError("Network error — the order was not placed.");
    } finally {
      setBusy(false);
    }
  }

  if (!ready) return <main style={{ padding: 32 }} />;

  if (!cart) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "32px 24px" }}>
        <div className="card" style={{ textAlign: "center", padding: 48 }}>
          <p style={{ fontSize: 40, margin: 0 }}>🛒</p>
          <p style={{ fontWeight: 600, margin: "8px 0 4px" }}>Your cart is empty</p>
          <p style={{ color: "var(--brand-muted)", margin: "0 0 16px" }}>
            Find a neighbor&rsquo;s kitchen and add a dish to get started.
          </p>
          <Link href="/" className="btn-primary" style={{ maxWidth: 240, margin: "0 auto" }}>
            Browse kitchens
          </Link>
        </div>
      </main>
    );
  }

  if (!session) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "32px 24px" }}>
        <div className="card" style={{ textAlign: "center", padding: 48 }}>
          <p style={{ fontSize: 40, margin: 0 }}>🔐</p>
          <p style={{ fontWeight: 600, margin: "8px 0 4px" }}>Log in to check out</p>
          <p style={{ color: "var(--brand-muted)", margin: "0 0 16px" }}>
            Your cart is saved — sign in and come right back.
          </p>
          <Link href="/login?next=/checkout" className="btn-primary" style={{ maxWidth: 240, margin: "0 auto" }}>
            Log in
          </Link>
        </div>
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 640, margin: "0 auto", padding: "32px 24px" }}>
      <Link href={`/kitchens/${cart.kitchenId}`} style={{ fontSize: 14 }}>
        ‹ Back to {cart.kitchenName}
      </Link>
      <h1 style={{ margin: "12px 0 16px", fontSize: 24, color: "var(--brand-green)" }}>Checkout</h1>

      {conflict && (
        <div className="form-error" role="alert">
          Some portions just sold out. Adjust the quantities below and try again.
        </div>
      )}
      {error && (
        <div className="form-error" role="alert">
          {error}
        </div>
      )}

      <div className="card" style={{ padding: 16, marginBottom: 16 }}>
        {cart.lines.map((line) => (
          <div
            key={line.menuItemId}
            style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 0" }}
          >
            <div style={{ flex: 1 }}>
              <strong style={{ fontSize: 15 }}>{line.dishName}</strong>
              <div style={{ fontSize: 13, color: "var(--brand-muted)" }}>{money(line.priceCents)} each</div>
            </div>
            <span className="qty-stepper">
              <button aria-label="Remove one" onClick={() => setQty(line.menuItemId, line.qty - 1)}>
                −
              </button>
              <span aria-live="polite">{line.qty}</span>
              <button aria-label="Add one" onClick={() => setQty(line.menuItemId, line.qty + 1)}>
                +
              </button>
            </span>
            <strong style={{ width: 64, textAlign: "right" }}>{money(line.priceCents * line.qty)}</strong>
          </div>
        ))}
      </div>

      <h2 style={{ fontSize: 17, color: "var(--brand-green)" }}>Ready time</h2>
      {menu === undefined && <div className="skeleton" style={{ height: 44 }} />}
      {menu === null && (
        <p style={{ color: "var(--brand-muted)" }}>
          This kitchen isn&rsquo;t serving today anymore. Your slot can&rsquo;t be booked.
        </p>
      )}
      {menu && slots.length === 0 && (
        <p style={{ color: "var(--brand-muted)" }}>No ready-time windows published for today.</p>
      )}
      {menu && slots.length > 0 && (
        <div className="pill-row" role="group" aria-label="Ready-time slot">
          {slots.map((s) => (
            <button
              key={s.iso}
              className="pill"
              aria-pressed={slot === s.iso}
              onClick={() => {
                setSlot(s.iso);
                setSummary(null);
              }}
            >
              ⏰ {s.label}
            </button>
          ))}
        </div>
      )}

      <h2 style={{ fontSize: 17, color: "var(--brand-green)", marginTop: 16 }}>Fulfillment</h2>
      <div className="role-toggle">
        <button
          aria-pressed={fulfillment === "pickup"}
          onClick={() => {
            setFulfillment("pickup");
            setSummary(null);
          }}
        >
          🏠 Pickup
        </button>
        <button
          aria-pressed={fulfillment === "delivery"}
          onClick={() => {
            setFulfillment("delivery");
            setSummary(null);
          }}
        >
          🚗 Delivery
        </button>
      </div>
      {fulfillment === "delivery" && (
        <p style={{ fontSize: 13, color: "var(--brand-muted)", margin: "-8px 0 8px" }}>
          The delivery fee is quoted by the courier when your order is prepared, so it isn&rsquo;t
          in the total below yet.
        </p>
      )}

      {!summary ? (
        <>
          <div style={{ display: "flex", justifyContent: "space-between", margin: "16px 0" }}>
            <span style={{ color: "var(--brand-muted)" }}>Subtotal</span>
            <strong>{money(subtotal)}</strong>
          </div>
          <button className="btn-primary" disabled={!slot || busy || !menu} onClick={review}>
            {busy ? "Pricing…" : "Review order"}
          </button>
        </>
      ) : (
        <div className="card" style={{ marginTop: 16, borderColor: "var(--brand-orange)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
            <span>Ready at</span>
            <strong>{slot?.slice(11)}</strong>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
            <span>Fulfillment</span>
            <strong style={{ textTransform: "capitalize" }}>{fulfillment}</strong>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
            <span>Total{fulfillment === "delivery" ? " (before delivery fee)" : ""}</span>
            <strong>{money(summary.totalCents)}</strong>
          </div>
          <p style={{ fontSize: 12, color: "var(--brand-muted)", marginTop: 0 }}>
            {fulfillment === "pickup"
              ? "The kitchen's address appears on your order once it's placed."
              : "You'll get a courier tracking link when the kitchen marks it ready."}{" "}
            Card payment arrives in a later update — placing now reserves your portions.
          </p>
          <button className="btn-primary" disabled={busy} onClick={place}>
            {busy ? "Placing…" : "Place order"}
          </button>
          <button
            onClick={() => setSummary(null)}
            disabled={busy}
            style={{
              width: "100%",
              marginTop: 8,
              background: "transparent",
              border: "none",
              color: "var(--brand-muted)",
              cursor: "pointer",
              fontSize: 14,
            }}
          >
            Back
          </button>
        </div>
      )}
    </main>
  );
}
