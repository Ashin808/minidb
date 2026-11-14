import { useEffect, useState } from "react";
import { listTables, runQuery, seedDemo } from "./api";
import "./App.css";

const EXAMPLE = "SELECT * FROM users WHERE id = 2";
const MAX_SHOWN = 100;   // a SELECT can return thousands of rows; only draw the first ones

export default function App() {
    const [sql, setSql] = useState(EXAMPLE);
    const [result, setResult] = useState(null);
    const [error, setError] = useState("");
    const [running, setRunning] = useState(false);
    const [tables, setTables] = useState([]);
    const [seedRows, setSeedRows] = useState(1000);
    const [seedNote, setSeedNote] = useState("");
    const [comparison, setComparison] = useState(null);

    async function refreshTables() {
        try {
            setTables(await listTables());
        } catch {
            setTables([]);   // the error box from run() already tells the user the API is down
        }
    }

    useEffect(() => {
        refreshTables();
    }, []);

    async function run() {
        if (running || !sql.trim()) return;
        setRunning(true);
        setError("");
        setComparison(null);
        try {
            setResult(await runQuery(sql));
        } catch (e) {
            setResult(null);
            setError(e.message);
        } finally {
            setRunning(false);
            refreshTables();   // CREATE TABLE / CREATE INDEX / INSERT change what the sidebar shows
        }
    }

    async function compare() {
        if (running || !sql.trim()) return;
        if (!/^\s*select\b/i.test(sql)) {
            setError("Compare only works with SELECT (it runs the query several times).");
            return;
        }
        setRunning(true);
        setError("");
        setComparison(null);
        try {
            await runQuery(sql, false);                 // warm-up runs, so the JVM and file cache are fair to both
            await runQuery(sql, true);
            const scan = await runQuery(sql, false);
            const index = await runQuery(sql, true);
            setComparison({ scan, index });
            setResult(index);
        } catch (e) {
            setResult(null);
            setError(e.message);
        } finally {
            setRunning(false);
        }
    }

    async function seed() {
        if (running) return;
        setRunning(true);
        setError("");
        setSeedNote("Adding rows...");
        try {
            const r = await seedDemo(seedRows);
            setSeedNote(`Added ${r.inserted} rows to ${r.table} (${r.totalRows} total) in ${r.elapsedMs.toFixed(0)} ms`);
        } catch (e) {
            setSeedNote("");
            setError(e.message);
        } finally {
            setRunning(false);
            refreshTables();
        }
    }

    function onKeyDown(e) {
        if (e.ctrlKey && e.key === "Enter") run();
    }

    return (
        <div className="layout">
            <aside className="sidebar">
                <h2>Tables</h2>
                {tables.length === 0 && <p className="hint">No tables yet</p>}
                {tables.map((t) => (
                    <div key={t.name} className="table-card">
                        <button className="link" onClick={() => setSql(`SELECT * FROM ${t.name}`)}>
                            {t.name}
                        </button>
                        <div className="cols">
                            {t.columns.map((c) => (
                                <div key={c.name}>
                                    {c.name} {c.type}
                                    {c.maxLength != null ? `(${c.maxLength})` : ""}
                                </div>
                            ))}
                        </div>
                        <div className="hint">{t.pages} pages</div>
                        {t.indexes.map((ix) => (
                            <div key={ix.column} className="index-tag">
                                index on {ix.column}: {ix.entries} entries, height {ix.height}
                            </div>
                        ))}
                    </div>
                ))}

                <h2>Demo data</h2>
                <div className="seed">
                    <input
                        type="number"
                        min="1"
                        max="20000"
                        value={seedRows}
                        onChange={(e) => setSeedRows(Number(e.target.value))}
                    />
                    <button onClick={seed} disabled={running}>Add rows</button>
                </div>
                <p className="hint">Adds rows to table <b>demo</b> (created if missing). Max 20000.</p>
                {seedNote && <p className="hint">{seedNote}</p>}
            </aside>

            <main className="page">
                <h1>MiniDB playground</h1>

                <textarea
                    className="sql"
                    value={sql}
                    onChange={(e) => setSql(e.target.value)}
                    onKeyDown={onKeyDown}
                    rows={5}
                    spellCheck={false}
                />
                <div className="actions">
                    <button onClick={run} disabled={running}>
                        {running ? "Running..." : "Run"}
                    </button>
                    <button onClick={compare} disabled={running}>Compare scan vs index</button>
                    <span className="hint">Ctrl+Enter</span>
                </div>

                {error && <div className="error">{error}</div>}
                {comparison && <CompareView scan={comparison.scan} index={comparison.index} />}
                {result && <ResultView result={result} />}
            </main>
        </div>
    );
}

function CompareView({ scan, index }) {
    const sameTable = scan.plan === index.plan;
    return (
        <section className="compare">
            <h3>Full scan vs index</h3>
            {sameTable ? (
                <p className="hint">
                    No index can answer this query (needs <b>col = value</b> on an indexed column), so both runs used: {scan.plan}.
                </p>
            ) : (
                <div className="compare-grid">
                    <div className="card">
                        <b>{scan.plan}</b>
                        <div>pages read: {scan.pagesRead}</div>
                        <div>time: {scan.elapsedMs.toFixed(3)} ms</div>
                    </div>
                    <div className="card good">
                        <b>{index.plan}</b>
                        <div>pages read: {index.pagesRead}</div>
                        <div>time: {index.elapsedMs.toFixed(3)} ms</div>
                    </div>
                </div>
            )}
            {!sameTable && (
                <p>
                    Index read <b>{(scan.pagesRead / index.pagesRead).toFixed(1)}x fewer pages</b> and
                    was <b>{(scan.elapsedMs / index.elapsedMs).toFixed(1)}x faster</b>.
                </p>
            )}
        </section>
    );
}

function ResultView({ result }) {
    const isQuery = result.columns.length > 0;
    const shown = result.rows.slice(0, MAX_SHOWN);
    return (
        <section>
            <p className="message">{result.message}</p>

            {isQuery && shown.length > 0 && (
                <div className="table-wrap">
                    <table>
                        <thead>
                        <tr>
                            {result.columns.map((c) => (
                                <th key={c}>{c}</th>
                            ))}
                        </tr>
                        </thead>
                        <tbody>
                        {shown.map((row, i) => (
                            <tr key={i}>
                                {row.map((cell, j) => (
                                    <td key={j}>{String(cell)}</td>
                                ))}
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
            {result.rows.length > MAX_SHOWN && (
                <p className="hint">Showing the first {MAX_SHOWN} of {result.rows.length} rows.</p>
            )}

            <div className="stats">
                {result.plan && <span>plan: <b>{result.plan}</b></span>}
                {result.plan && <span>pages read: <b>{result.pagesRead}</b></span>}
                <span>time: <b>{result.elapsedMs.toFixed(3)} ms</b></span>
            </div>
        </section>
    );
}