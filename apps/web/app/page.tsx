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
    <main style={{ maxWidth: 1080, margin: "0 auto", padding: "0 24px" }}>
      <nav
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          height: 68,
        }}
      >
        <span style={{ fontWeight: 700, fontSize: 18, letterSpacing: "-0.02em" }}>
          Nanas&rsquo; Kitchens
        </span>
        <Link href="/login" className="btn btn-ghost" style={{ padding: "8px 18px", fontSize: 14 }}>
          Sign in
        </Link>
      </nav>

      <section
        className="fade-up"
        style={{ padding: "72px 0 64px", maxWidth: 640 }}
      >
        <h1
          style={{
            fontSize: "clamp(38px, 6vw, 58px)",
            lineHeight: 1.04,
            letterSpacing: "-0.03em",
            fontWeight: 700,
            margin: 0,
          }}
        >
          Home-cooked food from real kitchens near you.
        </h1>
        <p
          style={{
            fontSize: 17,
            lineHeight: 1.6,
            color: "var(--text-2)",
            margin: "20px 0 32px",
            maxWidth: "46ch",
          }}
        >
          Cultural dishes made daily by home cooks within 10 miles. Chat, pick, and order.
        </p>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
          <Link href="/chat" className="btn btn-primary">
            Start an order
          </Link>
        </div>
      </section>

      <section style={{ paddingBottom: 96 }}>
        <h2
          style={{
            fontSize: 22,
            fontWeight: 700,
            letterSpacing: "-0.02em",
            margin: "0 0 8px",
          }}
        >
          Cooking today
        </h2>
        {kitchens.length > 0 ? (
          <div>
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
        ) : (
          <p style={{ color: "var(--text-2)", fontSize: 15, lineHeight: 1.6 }}>
            No kitchens are publishing menus right now. Check back soon, or start a chat and ask
            what&rsquo;s cooking.
          </p>
        )}
      </section>
    </main>
  );
}
