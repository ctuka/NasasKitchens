/** Story 1.1 placeholder — gercek alici arayuzu Epic 3'te front-end-spec.md'ye gore insa edilecek. */
const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export default async function Home() {
  let api = "unreachable";
  try {
    const res = await fetch(`${API}/health`, { cache: "no-store" });
    api = (await res.json()).db === "up" ? "up (db connected)" : "up (db down)";
  } catch {}
  return (
    <main style={{ padding: 32 }}>
      <h1>Nanas' Kitchens — dev shell</h1>
      <p>API: {api}</p>
    </main>
  );
}
