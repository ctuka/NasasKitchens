import Link from "next/link";

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

// Dev default: Lefkoşa. Real buyer location arrives with Epic 3's location story.
const DEV_LOCATION = { lat: 35.1856, lng: 33.3823 };

interface KitchenResult {
  id: string;
  name: string;
  cuisineTag: string;
  distanceMiles: number;
  portionsLeftToday: number;
}

async function getKitchens(): Promise<KitchenResult[]> {
  try {
    const res = await fetch(
      `${API}/kitchens/search?lat=${DEV_LOCATION.lat}&lng=${DEV_LOCATION.lng}`,
      { cache: "no-store" },
    );
    if (!res.ok) return [];
    return await res.json();
  } catch {
    return [];
  }
}

export default async function Home() {
  const kitchens = await getKitchens();

  return (
    <main style={{ maxWidth: 1080, margin: "0 auto", padding: "14px 24px 0" }}>
      <nav className="island-nav" style={{ maxWidth: 560, margin: "0 auto" }}>
        <span style={{ fontWeight: 700, fontSize: 16, letterSpacing: "-0.02em" }}>
          Nanas&rsquo; Kitchens
        </span>
        <Link href="/login" className="btn btn-primary" style={{ padding: "9px 20px", fontSize: 14 }}>
          Sign in
        </Link>
      </nav>

      <section style={{ padding: "96px 0 80px", maxWidth: 680 }}>
        <h1
          className="stagger"
          style={
            {
              "--i": 0,
              fontSize: "clamp(40px, 6.5vw, 64px)",
              lineHeight: 1.02,
              letterSpacing: "-0.035em",
              fontWeight: 700,
              margin: 0,
            } as React.CSSProperties
          }
        >
          Home-cooked food from real kitchens near you.
        </h1>
        <p
          className="stagger"
          style={
            {
              "--i": 1,
              fontSize: 17,
              lineHeight: 1.6,
              color: "var(--text-2)",
              margin: "22px 0 34px",
              maxWidth: "44ch",
            } as React.CSSProperties
          }
        >
          Cultural dishes made daily by home cooks within 10 miles. Chat, pick, and order.
        </p>
        <div className="stagger" style={{ "--i": 2, display: "flex", gap: 12 } as React.CSSProperties}>
          <Link href="/chat" className="btn btn-primary" style={{ padding: "12px 12px 12px 26px" }}>
            Start an order
            <span className="btn-orb" aria-hidden="true">&#8599;</span>
          </Link>
        </div>
      </section>

      <section className="stagger" style={{ "--i": 3, paddingBottom: 110 } as React.CSSProperties}>
        <h2
          style={{
            fontSize: 22,
            fontWeight: 700,
            letterSpacing: "-0.02em",
            margin: "0 0 16px",
            paddingLeft: 4,
          }}
        >
          Cooking today
        </h2>
        {kitchens.length > 0 ? (
          <div className="shell">
            <div className="shell-core" style={{ padding: 8 }}>
              {kitchens.map((k) => (
                <div key={k.id} className="kitchen-row">
                  <div className="monogram">{k.name.charAt(0)}</div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 16 }}>{k.name}</div>
                    <div style={{ fontSize: 14, color: "var(--text-2)", marginTop: 2 }}>
                      {k.cuisineTag.charAt(0).toUpperCase() + k.cuisineTag.slice(1)}
                      {" cuisine, "}
                      {k.distanceMiles} mi away
                    </div>
                  </div>
                  <span className="badge">
                    {k.portionsLeftToday} portion{k.portionsLeftToday === 1 ? "" : "s"} left
                  </span>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <p style={{ color: "var(--text-2)", fontSize: 15, lineHeight: 1.6, paddingLeft: 4 }}>
            No kitchens are publishing menus right now. Check back soon, or start a chat and ask
            what&rsquo;s cooking.
          </p>
        )}
      </section>
    </main>
  );
}
