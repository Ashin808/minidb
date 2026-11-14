// Where the Java API lives. Override with VITE_API_URL when deploying (6e).
const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

/** One place for fetch + error handling. A 400 carries the engine's own message in {error}. */
async function request(path, options) {
    let res;
    try {
        res = await fetch(`${BASE}${path}`, options);
    } catch {
        throw new Error("Cannot reach the MiniDB API. Is the Java app running?");
    }
    const data = await res.json();
    if (!res.ok) {
        throw new Error(data.error ?? `HTTP ${res.status}`);
    }
    return data;
}

export function runQuery(sql, useIndexes = true) {
    return request("/api/query", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sql, useIndexes }),
    });
}

export function listTables() {
    return request("/api/tables");
}

export function seedDemo(rows) {
    return request(`/api/demo/seed?rows=${rows}`, { method: "POST" });
}