"use client";

/** Kitchen Profile (front-end-spec.md, FR1/FR6/FR16-19). Reviews/polls/dish-requests
 * aren't in the API yet (Epic 6/7), so this page sticks to what's actually backed:
 * photos, hygiene badge, description, and today's live menu. */
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { API } from "../../../lib/api";
import { CUISINE_ICONS, CUISINE_LABELS } from "../../../lib/cuisines";

interface KitchenProfile {
  id: string;
  name: string;
  cuisineTag: string;
  description: string | null;
  photos: string[];
  ratingAvg: number | null;
  ratingCount: number;
  hygieneScoreTotal: number | null;
  hygieneScoredAt: string | null;
  complianceAttestedAt: string | null;
}

interface Dish {
  id: string;
  name: string;
  description: string | null;
  photo: string | null;
  priceCents: number;
  dietaryTags: string[];
}

interface MenuItem {
  id: string;
  portionsTotal: number;
  portionsRemaining: number;
  dish: Dish;
}

interface ReadyWindow {
  start: string;
  end: string;
  slotMinutes: number;
}

interface MenuDay {
  id: string;
  status: string;
  readyWindows: ReadyWindow[];
  items: MenuItem[];
}

const money = (cents: number) => `$${(cents / 100).toFixed(2)}`;

export default function KitchenProfilePage() {
  const { id } = useParams<{ id: string }>();
  const [profile, setProfile] = useState<KitchenProfile | null | "error">(null);
  const [menu, setMenu] = useState<MenuDay | null | undefined>(undefined);
  const [remaining, setRemaining] = useState<Record<string, number>>({});

  useEffect(() => {
    let cancelled = false;
    fetch(`${API}/kitchens/${id}`)
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((body) => !cancelled && setProfile(body))
      .catch(() => !cancelled && setProfile("error"));
    fetch(`${API}/kitchens/${id}/menu`)
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((body) => !cancelled && setMenu(body))
      .catch(() => !cancelled && setMenu(null));
    return () => {
      cancelled = true;
    };
  }, [id]);

  // Story 2.3 AC3: live portion counts via SSE, falling back to 10 s polling.
  useEffect(() => {
    if (typeof EventSource === "undefined") return;
    let pollTimer: ReturnType<typeof setInterval> | undefined;
    let opened = false;
    const source = new EventSource(`${API}/kitchens/${id}/portions/stream`);
    source.addEventListener("portions", (evt) => {
      opened = true;
      const payload = JSON.parse((evt as MessageEvent).data) as {
        items: { id: string; portionsRemaining: number }[];
      };
      setRemaining((prev) => {
        const next = { ...prev };
        for (const item of payload.items) next[item.id] = item.portionsRemaining;
        return next;
      });
    });
    source.onerror = () => {
      if (!opened && !pollTimer) {
        pollTimer = setInterval(() => {
          fetch(`${API}/kitchens/${id}/menu`)
            .then((res) => (res.ok ? res.json() : null))
            .then((body: MenuDay | null) => body && setMenu(body));
        }, 10000);
      }
    };
    return () => {
      source.close();
      if (pollTimer) clearInterval(pollTimer);
    };
  }, [id]);

  if (profile === "error") {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "32px 24px" }}>
        <div className="form-error" role="alert">
          Could not load this kitchen. It may not exist.
        </div>
        <Link href="/">‹ Back to nearby kitchens</Link>
      </main>
    );
  }

  if (profile === null) {
    return (
      <main style={{ maxWidth: 720, margin: "0 auto", padding: "32px 24px" }}>
        <div className="skeleton" style={{ height: 260, marginBottom: 24 }} />
      </main>
    );
  }

  const heroPhoto = profile.photos[0];
  const cuisineIcon = CUISINE_ICONS[profile.cuisineTag] ?? "🍽️";
  const cuisineLabel = CUISINE_LABELS[profile.cuisineTag] ?? profile.cuisineTag;

  return (
    <main style={{ maxWidth: 720, margin: "0 auto", padding: "32px 24px" }}>
      <Link href="/" style={{ fontSize: 14 }}>
        ‹ Back to nearby kitchens
      </Link>

      <div className="kitchen-photo" style={{ height: 220, marginTop: 12, borderRadius: 16, fontSize: 64 }}>
        {heroPhoto ? <img src={heroPhoto} alt="" /> : cuisineIcon}
      </div>
      {profile.photos.length > 1 && (
        <div style={{ display: "flex", gap: 8, marginTop: 8, overflowX: "auto" }}>
          {profile.photos.slice(1).map((src) => (
            <img
              key={src}
              src={src}
              alt=""
              style={{ width: 72, height: 72, borderRadius: 10, objectFit: "cover", flexShrink: 0 }}
            />
          ))}
        </div>
      )}

      <h1 style={{ margin: "20px 0 4px", fontSize: 26, color: "var(--brand-green)" }}>{profile.name}</h1>
      <div style={{ color: "var(--brand-muted)", fontSize: 14, marginBottom: 10 }}>
        {cuisineIcon} {cuisineLabel} ·{" "}
        {profile.ratingAvg != null ? `★ ${profile.ratingAvg.toFixed(1)} (${profile.ratingCount})` : "New kitchen"}
      </div>

      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 16 }}>
        {profile.hygieneScoreTotal != null ? (
          <span className="badge hygiene">
            🛡 Hygiene {profile.hygieneScoreTotal}
            {profile.hygieneScoredAt &&
              ` · inspected ${new Date(profile.hygieneScoredAt).toLocaleDateString()}`}
          </span>
        ) : (
          <span className="badge soldout">Not yet inspected</span>
        )}
      </div>

      {profile.description && (
        <p style={{ color: "var(--brand-ink)", lineHeight: 1.5 }}>{profile.description}</p>
      )}

      <h2 style={{ fontSize: 20, color: "var(--brand-green)", marginTop: 28 }}>Today&rsquo;s Menu</h2>

      {menu === undefined && (
        <div className="kitchen-grid">
          <div className="skeleton" />
          <div className="skeleton" />
        </div>
      )}

      {menu === null && (
        <div className="card" style={{ textAlign: "center", padding: 40 }}>
          <p style={{ fontSize: 36, margin: 0 }}>📭</p>
          <p style={{ fontWeight: 600, margin: "8px 0 4px" }}>No menu published today</p>
          <p style={{ color: "var(--brand-muted)", margin: 0 }}>
            Ask the <Link href="/chat">agent</Link> to notify you when {profile.name} publishes one.
          </p>
        </div>
      )}

      {menu && (
        <>
          {menu.readyWindows?.length > 0 && (
            <div className="pill-row" role="group" aria-label="Ready-time windows">
              {menu.readyWindows.map((w, i) => (
                <span key={i} className="pill" aria-pressed="false">
                  ⏰ {w.start}–{w.end}
                </span>
              ))}
            </div>
          )}

          <div className="kitchen-grid">
            {menu.items.map((item) => {
              const left = remaining[item.id] ?? item.portionsRemaining;
              const soldOut = left <= 0;
              return (
                <div key={item.id} className={`kitchen-card${soldOut ? " sold-out" : ""}`}>
                  <div className="kitchen-photo">
                    {item.dish.photo ? <img src={item.dish.photo} alt="" loading="lazy" /> : "🍽️"}
                  </div>
                  <div style={{ padding: "12px 16px 16px" }}>
                    <strong style={{ fontSize: 16 }}>{item.dish.name}</strong>
                    {item.dish.description && (
                      <p style={{ margin: "4px 0 8px", fontSize: 13, color: "var(--brand-muted)" }}>
                        {item.dish.description}
                      </p>
                    )}
                    <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 8 }}>
                      {item.dish.dietaryTags.map((tag) => (
                        <span key={tag} className="badge hygiene">
                          {tag}
                        </span>
                      ))}
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <strong>{money(item.dish.priceCents)}</strong>
                      {soldOut ? (
                        <span className="badge soldout">Sold out</span>
                      ) : (
                        <span className="badge portions">{left} left</span>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          <Link
            href="/chat"
            className="card"
            style={{
              display: "flex",
              alignItems: "center",
              gap: 16,
              marginTop: 24,
              textDecoration: "none",
              color: "var(--brand-ink)",
            }}
          >
            <span style={{ fontSize: 32 }}>💬</span>
            <span>
              <strong>Order via the AI Chat Assistant</strong>
              <br />
              <span style={{ color: "var(--brand-muted)", fontSize: 14 }}>
                Tell it what you&rsquo;d like from {profile.name} and a ready time.
              </span>
            </span>
          </Link>
        </>
      )}
    </main>
  );
}
