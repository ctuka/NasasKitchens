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

function cap(s: string) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

// Dev photo mapping (real CC photos in /public/dishes) until sellers upload their own.
const KITCHEN_PHOTOS: Record<string, string> = {
  "Emine's Manti Evi": "/dishes/manti.jpg",
  "Havva's Sarma Kosesi": "/dishes/sarma.jpg",
  "Fatma's Sarma House": "/dishes/dolma.jpg",
  "Ayse's Anatolian Kitchen": "/dishes/lentil.jpg",
  "Zeynep's Gozleme House": "/dishes/gozleme.jpg",
  "Abeba's Injera Kitchen": "/dishes/injera.jpg",
  "Mei's Sichuan Home Cooking": "/dishes/mapo.jpg",
  "Rosa's Cocina Oaxaquena": "/dishes/mole.jpg",
};

const CUISINE_PHOTOS: Record<string, string> = {
  turkish: "/dishes/manti.jpg",
  chinese: "/dishes/mapo.jpg",
  mexican: "/dishes/mole.jpg",
  ethiopian: "/dishes/injera.jpg",
};

function photoFor(k: KitchenResult) {
  return KITCHEN_PHOTOS[k.name] ?? CUISINE_PHOTOS[k.cuisineTag] ?? "/dishes/dolma.jpg";
}

export default async function Home() {
  const kitchens = await getKitchens();
  const totalPortions = kitchens.reduce((sum, k) => sum + k.portionsLeftToday, 0);
  const cuisines = new Set(kitchens.map((k) => k.cuisineTag)).size;

  return (
    <main style={{ maxWidth: 1120, margin: "0 auto", padding: "14px 24px 0", position: "relative" }}>
      <div className="hero-glow" aria-hidden="true" />

      <nav className="island-nav" style={{ maxWidth: 560, margin: "0 auto" }}>
        <span style={{ fontWeight: 700, fontSize: 16, letterSpacing: "-0.02em" }}>
          Nanas&rsquo; Kitchens
        </span>
        <Link href="/login" className="btn btn-primary" style={{ padding: "9px 20px", fontSize: 14 }}>
          Sign in
        </Link>
      </nav>

      <section className="hero-grid">
        <div>
          <h1
            className="stagger"
            style={
              {
                "--i": 0,
                fontSize: "clamp(44px, 7vw, 76px)",
                lineHeight: 1.0,
                letterSpacing: "-0.04em",
                fontWeight: 700,
                margin: 0,
              } as React.CSSProperties
            }
          >
            Cooked at <span className="hero-em">home,</span>
            <br />
            not at a restaurant.
          </h1>
          <p
            className="stagger"
            style={
              {
                "--i": 1,
                fontSize: 18,
                lineHeight: 1.6,
                color: "var(--text-2)",
                margin: "24px 0 36px",
                maxWidth: "42ch",
              } as React.CSSProperties
            }
          >
            Cultural dishes made daily by home cooks within 10 miles. Chat, pick, and order.
          </p>
          <div className="stagger" style={{ "--i": 2, display: "flex", gap: 12 } as React.CSSProperties}>
            <Link href="/chat" className="btn btn-primary" style={{ padding: "13px 13px 13px 28px", fontSize: 16 }}>
              Start an order
              <span className="btn-orb" aria-hidden="true">&#8599;</span>
            </Link>
          </div>
        </div>

        {kitchens.length > 0 && (
          <div className="cascade stagger" style={{ "--i": 2 } as React.CSSProperties}>
            {kitchens.slice(0, 3).map((k, i) => (
              <div key={k.id} className={`cascade-card hue-${i % 5}`}>
                <img src={photoFor(k)} alt="" className="cascade-photo" />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: 15, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                    {k.name}
                  </div>
                  <div style={{ fontSize: 13, color: "var(--text-2)", marginTop: 2 }}>
                    {cap(k.cuisineTag)}, {k.distanceMiles} mi
                  </div>
                </div>
                <span className="badge">{k.portionsLeftToday} left</span>
              </div>
            ))}
          </div>
        )}
      </section>

      {kitchens.length > 0 && (
        <section className="stagger stat-strip" style={{ "--i": 3 } as React.CSSProperties}>
          <div className="stat">
            <div className="stat-value">{kitchens.length}</div>
            <div className="stat-label">kitchens cooking today</div>
          </div>
          <div className="stat">
            <div className="stat-value">{totalPortions}</div>
            <div className="stat-label">portions still available</div>
          </div>
          <div className="stat">
            <div className="stat-value">{cuisines}</div>
            <div className="stat-label">cuisine{cuisines === 1 ? "" : "s"} nearby</div>
          </div>
        </section>
      )}

      <section className="stagger" style={{ "--i": 4, padding: "72px 0 110px" } as React.CSSProperties}>
        <h2
          style={{
            fontSize: 26,
            fontWeight: 700,
            letterSpacing: "-0.025em",
            margin: "0 0 20px",
          }}
        >
          Cooking today
        </h2>
        {kitchens.length > 0 ? (
          <div className="kitchen-grid">
            {kitchens.map((k, i) => (
              <div key={k.id} className={`kitchen-card hue-${i % 5}`}>
                <img src={photoFor(k)} alt={`${k.name} dish`} className="kitchen-photo" />
                <div className="kitchen-card-body">
                  <div className="monogram" style={{ width: 44, height: 44, fontSize: 17 }}>
                    {k.name.charAt(0)}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 16.5 }}>{k.name}</div>
                    <div style={{ fontSize: 14, color: "var(--text-2)", marginTop: 3 }}>
                      {cap(k.cuisineTag)} cuisine, {k.distanceMiles} mi away
                    </div>
                  </div>
                  <span className="badge">
                    {k.portionsLeftToday} portion{k.portionsLeftToday === 1 ? "" : "s"}
                  </span>
                </div>
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
