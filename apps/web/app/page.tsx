/** Gecici karsilama — gercek Buyer Home (yakin mutfak kartlari) Faz 2'de insa edilecek. */
const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default async function Home() {
  let api = "unreachable";
  try {
    const res = await fetch(`${API}/health`, { cache: "no-store" });
    api = (await res.json()).db === "up" ? "up (db connected)" : "up (db down)";
  } catch {}
  return (
    <main style={{ maxWidth: 720, margin: "56px auto", padding: "0 24px", textAlign: "center" }}>
      <h1 style={{ fontSize: 40, margin: "0 0 8px", color: "var(--brand-green)" }}>
        Real Food. <span style={{ color: "var(--brand-orange)" }}>Made by Neighbors.</span>
      </h1>
      <p style={{ fontSize: 18, color: "var(--brand-muted)", margin: "0 0 32px" }}>
        Local kitchens. Real recipes. Made with love. Just for you.
      </p>
      <div style={{ display: "flex", gap: 12, justifyContent: "center" }}>
        <a
          href="/register"
          style={{
            background: "var(--brand-orange)",
            color: "#fff",
            borderRadius: 10,
            padding: "12px 28px",
            textDecoration: "none",
            fontWeight: 700,
          }}
        >
          Get started
        </a>
        <a
          href="/chat"
          style={{
            border: "1px solid var(--brand-border)",
            background: "#fff",
            borderRadius: 10,
            padding: "12px 28px",
            textDecoration: "none",
            fontWeight: 600,
          }}
        >
          Try the AI assistant
        </a>
      </div>
      <p style={{ marginTop: 48, fontSize: 13, color: "var(--brand-muted)" }}>API: {api}</p>
    </main>
  );
}
