"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { apiFetch, getSession, logout, Session } from "../../lib/api";

interface AppNotification {
  id: string;
  type: string;
  title: string;
  body: string;
  data: { orderId?: string; trackingUrl?: string; kitchenId?: string } | null;
  readAt: string | null;
  createdAt: string;
}

export default function Header() {
  const [session, setSession] = useState<Session | null>(null);
  const router = useRouter();

  useEffect(() => {
    const sync = () => setSession(getSession());
    sync();
    window.addEventListener("session-changed", sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener("session-changed", sync);
      window.removeEventListener("storage", sync);
    };
  }, []);

  return (
    <header
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "14px 24px",
        background: "#fff",
        borderBottom: "1px solid var(--brand-border)",
      }}
    >
      <Link href="/" style={{ textDecoration: "none", display: "flex", alignItems: "baseline", gap: 6 }}>
        <span style={{ fontSize: 22, fontWeight: 800, color: "var(--brand-green)" }}>Nanas&rsquo;</span>
        <span style={{ fontSize: 22, fontWeight: 800, color: "var(--brand-orange)" }}>Kitchens</span>
      </Link>
      <nav style={{ display: "flex", alignItems: "center", gap: 18, fontSize: 15 }}>
        <Link href="/">Home</Link>
        <Link href="/chat">Chat</Link>
        {session?.role === "seller" && <Link href="/seller/orders">Orders</Link>}
        {session?.role === "seller" && <Link href="/seller/menu">My Menu</Link>}
        {session?.role === "seller" && <Link href="/seller/kitchen">My Kitchen</Link>}
        {session?.role === "seller" && <Link href="/seller/earnings">Earnings</Link>}
        {session?.role === "inspector" && <Link href="/inspector/visits">Visits</Link>}
        {session?.role === "admin" && <Link href="/admin">Admin</Link>}
        {session ? (
          <>
            <NotificationBell session={session} />
            <span
              style={{
                background: "var(--brand-green)",
                color: "#fff",
                borderRadius: 999,
                padding: "3px 12px",
                fontSize: 13,
                textTransform: "capitalize",
              }}
            >
              {session.role}
            </span>
            <button
              onClick={() => {
                logout();
                router.push("/");
              }}
              style={{
                background: "transparent",
                border: "1px solid var(--brand-border)",
                borderRadius: 8,
                padding: "6px 14px",
                cursor: "pointer",
                fontSize: 14,
              }}
            >
              Log out
            </button>
          </>
        ) : (
          <>
            <Link href="/login">Log in</Link>
            <Link
              href="/register"
              style={{
                background: "var(--brand-orange)",
                color: "#fff",
                borderRadius: 8,
                padding: "8px 16px",
                textDecoration: "none",
                fontWeight: 600,
              }}
            >
              Sign up
            </Link>
          </>
        )}
      </nav>
    </header>
  );
}

/** Story 4.4 (FR22) — in-app notification inbox. Polls every 30 s; opening the panel
 * marks everything read. Buyer notifications deep-link to the order page; seller
 * order notifications deep-link to the Today Board (Story 4.1). */
function NotificationBell({ session }: { session: Session }) {
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [open, setOpen] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);

  async function refresh() {
    try {
      const res = await apiFetch("/notifications");
      if (!res.ok) return;
      const body = await res.json();
      setUnread(body.unreadCount);
      setItems(body.notifications);
    } catch {
      /* API down — badge just goes stale */
    }
  }

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 30000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session.userId]);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [open]);

  async function toggle() {
    const next = !open;
    setOpen(next);
    if (next && unread > 0) {
      await apiFetch("/notifications/read", { method: "POST" }).catch(() => {});
      setUnread(0);
    }
  }

  return (
    <div ref={panelRef} style={{ position: "relative" }}>
      <button className="bell" aria-label={`Notifications${unread ? ` (${unread} unread)` : ""}`} onClick={toggle}>
        🔔
        {unread > 0 && <span className="bell-badge">{unread > 9 ? "9+" : unread}</span>}
      </button>
      {open && (
        <div className="notif-panel" role="menu" aria-label="Notifications">
          {items.length === 0 && (
            <p style={{ padding: 16, margin: 0, color: "var(--brand-muted)", fontSize: 14 }}>
              Nothing yet — order something delicious!
            </p>
          )}
          {items.map((n) => {
            const time = new Date(n.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
            const inner = (
              <>
                <strong style={{ fontSize: 14 }}>{n.title}</strong>
                <span style={{ fontSize: 13, color: "var(--brand-muted)" }}>{n.body}</span>
                <span style={{ fontSize: 11, color: "var(--brand-muted)" }}>{time}</span>
              </>
            );
            // Deep-link by role: buyers to the order/kitchen, sellers to the board or
            // (for dish requests, which carry only kitchenId) their menu inbox.
            const href =
              session.role === "buyer"
                ? n.data?.orderId
                  ? `/orders/${n.data.orderId}`
                  : n.data?.kitchenId
                    ? `/kitchens/${n.data.kitchenId}`
                    : null
                : session.role === "seller"
                  ? n.data?.orderId
                    ? "/seller/orders"
                    : n.data?.kitchenId
                      ? "/seller/menu"
                      : null
                  : null;
            return href ? (
              <Link
                key={n.id}
                href={href}
                className={`notif-item${n.readAt ? "" : " unread"}`}
                onClick={() => setOpen(false)}
              >
                {inner}
              </Link>
            ) : (
              <div key={n.id} className={`notif-item${n.readAt ? "" : " unread"}`}>
                {inner}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
