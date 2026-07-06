"use client";

/** Order confirmation & tracking (front-end-spec.md Order Tracking, FR10). The pickup street
 * address is only present here once the order is placed — the server withholds it otherwise. */
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { apiFetch } from "../../../lib/api";
import { money } from "../../../lib/cart";

interface OrderItem {
  id: string;
  qty: number;
  unitPriceCents: number;
  menuItem: { dish: { name: string; photo: string | null } };
}

interface DeliveryJob {
  provider: string;
  status: string;
  trackingUrl: string | null;
  feeCents: number;
}

interface OrderDetail {
  id: string;
  status: string;
  readySlot: string;
  fulfillment: string;
  totalCents: number;
  items: OrderItem[];
  kitchenName: string;
  pickupAddress: string | null;
  deliveryJob: DeliveryJob | null;
}

const STATUS_LABEL: Record<string, string> = {
  confirmed: "Confirmed — waiting for the kitchen to accept",
  accepted: "Accepted by the kitchen",
  preparing: "Being prepared",
  ready: "Ready",
  completed: "Completed",
  declined: "Declined by the kitchen",
  cancelled: "Cancelled",
};

const FINAL = new Set(["completed", "cancelled", "declined"]);

export default function OrderPage() {
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<OrderDetail | null | "error">(null);
  const [cancelling, setCancelling] = useState(false);

  async function load() {
    try {
      const res = await apiFetch(`/orders/${id}`);
      if (!res.ok) return setOrder("error");
      setOrder(await res.json());
    } catch {
      setOrder("error");
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function cancel() {
    setCancelling(true);
    try {
      await apiFetch(`/orders/${id}/cancel`, { method: "POST" });
      await load();
    } finally {
      setCancelling(false);
    }
  }

  if (order === null) {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "32px 24px" }}>
        <div className="skeleton" style={{ height: 180 }} />
      </main>
    );
  }

  if (order === "error") {
    return (
      <main style={{ maxWidth: 640, margin: "0 auto", padding: "32px 24px" }}>
        <div className="form-error" role="alert">
          Could not load this order. You can only view your own orders.
        </div>
        <Link href="/">‹ Back to home</Link>
      </main>
    );
  }

  const readySlot = new Date(order.readySlot);

  return (
    <main style={{ maxWidth: 640, margin: "0 auto", padding: "32px 24px" }}>
      <div className="card" style={{ textAlign: "center", padding: "28px 24px", marginBottom: 16 }}>
        <p style={{ fontSize: 40, margin: 0 }}>🎉</p>
        <h1 style={{ margin: "8px 0 4px", fontSize: 22, color: "var(--brand-green)" }}>Order placed!</h1>
        <p style={{ color: "var(--brand-muted)", margin: 0 }}>{order.kitchenName}</p>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div style={{ marginBottom: 12 }}>
          <span className="badge portions">{STATUS_LABEL[order.status] ?? order.status}</span>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
          <span style={{ color: "var(--brand-muted)" }}>Ready at</span>
          <strong>
            {readySlot.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
          </strong>
        </div>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
          <span style={{ color: "var(--brand-muted)" }}>Fulfillment</span>
          <strong style={{ textTransform: "capitalize" }}>{order.fulfillment}</strong>
        </div>

        {order.fulfillment === "pickup" && order.pickupAddress && (
          <div style={{ marginTop: 10, padding: 12, background: "#fdf0e3", borderRadius: 10 }}>
            <div style={{ fontSize: 13, color: "var(--brand-muted)" }}>Pickup address</div>
            <strong>{order.pickupAddress}</strong>
          </div>
        )}

        {order.fulfillment === "delivery" && (
          <div style={{ marginTop: 10, padding: 12, background: "#fdf0e3", borderRadius: 10 }}>
            {order.deliveryJob?.trackingUrl ? (
              <a href={order.deliveryJob.trackingUrl} target="_blank" rel="noreferrer">
                Track your courier →
              </a>
            ) : (
              <span style={{ color: "var(--brand-muted)", fontSize: 14 }}>
                A courier tracking link appears once the kitchen marks your order ready.
              </span>
            )}
          </div>
        )}
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        {order.items.map((it) => (
          <div key={it.id} style={{ display: "flex", justifyContent: "space-between", padding: "6px 0" }}>
            <span>
              {it.qty} × {it.menuItem.dish.name}
            </span>
            <strong>{money(it.unitPriceCents * it.qty)}</strong>
          </div>
        ))}
        <hr style={{ border: "none", borderTop: "1px solid var(--brand-border)", margin: "10px 0" }} />
        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <strong>Total</strong>
          <strong>{money(order.totalCents)}</strong>
        </div>
      </div>

      {!FINAL.has(order.status) && (
        <button
          onClick={cancel}
          disabled={cancelling}
          style={{
            width: "100%",
            padding: "12px 20px",
            background: "transparent",
            border: "1px solid var(--brand-border)",
            borderRadius: 10,
            fontSize: 15,
            cursor: "pointer",
            color: "#b91c1c",
          }}
        >
          {cancelling ? "Cancelling…" : "Cancel order"}
        </button>
      )}
      <p style={{ textAlign: "center", marginTop: 16 }}>
        <Link href="/">Back to nearby kitchens</Link>
      </p>
    </main>
  );
}
