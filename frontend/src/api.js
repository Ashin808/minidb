// Where the Java API lives. Override with VITE_API_URL when deploying (6e).
const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

/** Sends SQL to the engine. Resolves with the JSON reply, rejects with an Error whose message is shown to the user. */
export async function runQuery(sql) {
    let res;
    try {
        res = await fetch(`${BASE}/api/query`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ sql }),
        });
    } catch {
        throw new Error("Cannot reach the MiniDB API. Is the Java app running?");
    }
    const data = await res.json();
    if (!res.ok) {
        throw new Error(data.error ?? `HTTP ${res.status}`);   // 400 = user error from SqlException
    }
    return data;
}