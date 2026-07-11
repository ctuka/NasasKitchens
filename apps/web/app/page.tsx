"use client";

/** Buyer Home — Nearby Kitchens (front-end-spec.md, FR5). */
import Link from "next/link";
import { useEffect, useState } from "react";
import { API } from "../lib/api";
import { CUISINES, CUISINE_ICONS } from "../lib/cuisines";

interface KitchenResult {
  id: string;
  name: string;
  cuisineTag: string;
  distanceMiles: number;
  ratingAvg: number | null;
  hygieneScore: number | null;
  portionsLeftToday: number;
  photo: string | null;
}

// Demo fallback matches the seeded Powell, Ohio (43065) marketplace.
const DEMO_LOCATION = { lat: 40.1578, lng: -83.0752, label: "43065 (Powell, OH)" };

export default function BuyerHome() {
  const [location, setLocation] = useState<{ lat: number; lng: number; label: string } | null>(null);
  const [cuisine, setCuisine] = useState<string | null>(null);
  const [kitchens, setKitchens] = useState<KitchenResult[] | null>(null);
  const [error, setError] = useState(false);

  // Geolocation with demo fallback (spec: permission error → manual fallback).
  useEffect(() => {
    if (!navigator.geolocation) {
      setLocation(DEMO_LOCATION);
      return;
    }
    const timer = setTimeout(() => setLocation((l) => l ?? DEMO_LOCATION), 3000);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        clearTimeout(timer);
        setLocation({ lat: pos.coords.latitude, lng: pos.coords.longitude, label: "your location" });
      },
      () => {
        clearTimeout(timer);
        setLocation(DEMO_LOCATION);
      },
      { timeout: 2500 },
    );
    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (!location) return;
    let cancelled = false;
    setKitchens(null);
    setError(false);
    const qs = new URLSearchParams({ lat: String(location.lat), lng: String(location.lng) });
    if (cuisine) qs.set("cuisine", cuisine);
    fetch(`${API}/kitchens/search?${qs}`)
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((rows) => !cancelled && setKitchens(rows))
      .catch(() => !cancelled && setError(true));
    return () => {
      cancelled = true;
    };
  }, [location, cuisine]);

  return (
    <main style={{ maxWidth: 960, margin: "0 auto", padding: "32px 24px" }}>
      <h1 style={{ margin: "0 0 4px", fontSize: 28, color: "var(--brand-green)" }}>
        Hungry for something delicious? <span style={{ color: "var(--brand-orange)" }}>❤</span>
      </h1>
      <p style={{ margin: "0 0 16px", color: "var(--brand-muted)" }}>
        Made with ❤ near you — homemade meals within 10 miles of{" "}
        <strong>{location?.label ?? "…"}</strong>
      </p>

      <div className="pill-row" role="group" aria-label="Cuisine filter">
        <button className="pill" aria-pressed={cuisine === null} onClick={() => setCuisine(null)}>
          ✨ All
        </button>
        {CUISINES.map((c) => (
          <button
            key={c.tag}
            className="pill"
            aria-pressed={cuisine === c.tag}
            onClick={() => setCuisine(c.tag)}
          >
            {c.icon} {c.label}
          </button>
        ))}
      </div>

      {error && (
        <div className="form-error" role="alert">
          Could not load kitchens. Is the API running?
        </div>
      )}

      {!error && kitchens === null && (
        <div className="kitchen-grid" aria-label="Loading">
          {[0, 1, 2].map((i) => (
            <div key={i} className="skeleton" />
          ))}
        </div>
      )}

      {!error && kitchens !== null && kitchens.length === 0 && (
        <div className="card" style={{ textAlign: "center", padding: 48 }}>
          <p style={{ fontSize: 40, margin: 0 }}>🥘</p>
          <p style={{ fontWeight: 600, margin: "8px 0 4px" }}>No kitchens in your area yet</p>
          <p style={{ color: "var(--brand-muted)", margin: 0 }}>
            Ask the <Link href="/chat">agent</Link> to notify you when a neighbor starts cooking.
          </p>
        </div>
      )}

      {!error && kitchens !== null && kitchens.length > 0 && (
        <div className="kitchen-grid">
          {kitchens.map((k) => {
            const soldOut = k.portionsLeftToday === 0;
            return (
              <Link
                key={k.id}
                href={`/kitchens/${k.id}`}
                className={`kitchen-card${soldOut ? " sold-out" : ""}`}
              >
                <div className="kitchen-photo" aria-hidden>
                  {k.photo ? <img src={k.photo} alt="" loading="lazy" /> : CUISINE_ICONS[k.cuisineTag] ?? "🍽️"}
                  <span className="flag-tag">{CUISINE_ICONS[k.cuisineTag] ?? "🍽️"}</span>
                </div>
                <div style={{ padding: "12px 16px 16px" }}>
                  <strong style={{ fontSize: 16 }}>{k.name}</strong>
                  <div style={{ margin: "4px 0 8px", fontSize: 13, color: "var(--brand-muted)" }}>
                    {k.cuisineTag[0].toUpperCase() + k.cuisineTag.slice(1)} · {k.distanceMiles} mi ·{" "}
                    {k.ratingAvg != null ? `★ ${k.ratingAvg.toFixed(1)}` : "New"}
                  </div>
                  <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                    {k.hygieneScore != null && (
                      <span className="badge hygiene">🛡 Hygiene {k.hygieneScore}</span>
                    )}
                    {soldOut ? (
                      <span className="badge soldout">Sold out today</span>
                    ) : (
                      <span className="badge portions">{k.portionsLeftToday} portions left today</span>
                    )}
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      )}

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
          <strong>AI Chat Assistant</strong>
          <br />
          <span style={{ color: "var(--brand-muted)", fontSize: 14 }}>
            Tell me what you like, I&rsquo;ll recommend the best!
          </span>
        </span>
      </Link>
    </main>
  );
}
