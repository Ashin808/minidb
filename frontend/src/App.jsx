import { useEffect, useRef, useState } from "react";
import { getTree, listTables, runQuery, seedDemo } from "./api";
import TreeView from "./TreeView";
import "./App.css";

const EXAMPLE = "SELECT * FROM users WHERE id = 2";
const DEMO_QUERY = "SELECT * FROM demo WHERE id = 4000";
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
    const [treeTarget, setTreeTarget] = useState(null);   // { table, column } or null
    const [tree, setTree] = useState(null);
    const [treeError, setTreeError] = useState("");
    const [ranScan, setRanScan] = useState(false);        // guide step 2 was run

    const resultsRef = useRef(null);
    const treeRef = useRef(null);
    const scrollToTreeNext = useRef(false);

    const demo = tables.find((t) => t.name === "demo");
    const hasData = !!demo && demo.pages >= 10;
    const hasIndex = !!demo && demo.indexes.some((ix) => ix.column === "id");

    async function refreshTables() {
        try {
            setTables(await listTables());
        } catch {
            setTables([]);
        }
    }

    useEffect(() => {
        refreshTables();
    }, []);

    useEffect(() => {
        if (!treeTarget) {
            setTree(null);
            return;
        }
        let cancelled = false;
        getTree(treeTarget.table, treeTarget.column)
            .then((t) => {
                if (!cancelled) {
                    setTree(t);
                    setTreeError("");
                }
            })
            .catch((e) => {
                if (!cancelled) {
                    setTree(null);
                    setTreeError(e.message);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [treeTarget, tables]);

    useEffect(() => {
        if (result || comparison || error) {
            resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    }, [result, comparison, error]);

    useEffect(() => {
        if (tree && scrollToTreeNext.current) {
            scrollToTreeNext.current = false;
            treeRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    }, [tree]);

    async function run(text) {
        const q = typeof text === "string" ? text : sql;
        if (running || !q.trim()) return false;
        setSql(q);
        setRunning(true);
        setError("");
        setComparison(null);
        let ok = false;
        try {
            setResult(await runQuery(q));
            ok = true;
        } catch (e) {
            setResult(null);
            setError(e.message);
        } finally {
            setRunning(false);
            refreshTables();
        }
        return ok;
    }

    async function compare(text) {
        const q = typeof text === "string" ? text : sql;
        if (running || !q.trim()) return;
        setSql(q);
        setComparison(null);
        if (!/^\s*select\b/i.test(q)) {
            setError("Compare only works with SELECT (it runs the query several times).");
            return;
        }
        setRunning(true);
        setError("");
        try {
            await runQuery(q, false);                 // warm-up runs, so the JVM and file cache are fair to both
            await runQuery(q, true);
            const scan = await runQuery(q, false);
            const index = await runQuery(q, true);
            setComparison({ scan, index });
            setResult(index);
        } catch (e) {
            setResult(null);
            setError(e.message);
        } finally {
            setRunning(false);
        }
    }

    async function seed(n) {
        if (running) return;
        setRunning(true);
        setError("");
        setSeedNote("Adding rows...");
        try {
            const r = await seedDemo(n);
            setSeedNote(`Added ${r.inserted} rows to ${r.table} (${r.totalRows} total) in ${r.elapsedMs.toFixed(0)} ms`);
        } catch (e) {
            setSeedNote("");
            setError(e.message);
        } finally {
            setRunning(false);
            refreshTables();
        }
    }

    function showTree(table, column) {
        scrollToTreeNext.current = true;
        setTreeTarget({ table, column });
    }

    function onKeyDown(e) {
        if (e.ctrlKey && e.key === "Enter") run();
    }

    const steps = [
        {
            title: "Add 5000 rows",
            text: "Creates a table called demo and fills it. Rows are stored in real 4 KB pages on disk.",
            button: hasData ? "Done" : "Add 5000 rows",
            done: hasData,
            disabled: hasData,
            action: () => seed(5000),
        },
        {
            title: "Find one row without an index",
            text: "The engine must read every page of the table to find id 4000.",
            button: "Run the query",
            done: ranScan,
            disabled: !hasData,
            action: async () => {
                if (await run(DEMO_QUERY)) setRanScan(true);
            },
        },
        {
            title: "Create an index",
            text: "Builds a B+ tree on the id column and saves it in its own file.",
            button: hasIndex ? "Done" : "Create index",
            done: hasIndex,
            disabled: !hasData || hasIndex,
            action: () => run("CREATE INDEX ON demo (id)"),
        },
        {
            title: "Compare scan vs index",
            text: "Runs the same query both ways and shows which pages each one had to read.",
            button: "Compare",
            done: !!comparison,
            disabled: !hasIndex,
            action: () => compare(DEMO_QUERY),
        },
        {
            title: "Look inside the index",
            text: "Draws the real tree: each box is one page of the index file.",
            button: "Show the tree",
            done: treeTarget?.table === "demo",
            disabled: !hasIndex,
            action: () => showTree("demo", "id"),
        },
    ];

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
                            <button
                                key={ix.column}
                                className="index-tag"
                                title="Click to draw this B+ tree"
                                onClick={() => showTree(t.name, ix.column)}
                            >
                                index on {ix.column}: {ix.entries} entries, height {ix.height}
                            </button>
                        ))}
                    </div>
                ))}

                <h2>Add more data</h2>
                <div className="seed">
                    <input
                        type="number"
                        min="1"
                        max="20000"
                        value={seedRows}
                        onChange={(e) => setSeedRows(Number(e.target.value))}
                    />
                    <button onClick={() => seed(seedRows)} disabled={running}>Add rows</button>
                </div>
                <p className="hint">Adds rows to table <b>demo</b>. Max 20000.</p>
                {seedNote && <p className="hint">{seedNote}</p>}
            </aside>

            <main className="page">
                <header className="intro">
                    <h1>MiniDB</h1>
                    <p className="tagline">
                        A relational database engine built from scratch in Java: its own file storage, SQL parser,
                        query executor and on-disk B+ tree index. No database library underneath.
                    </p>
                </header>

                <section className="guide">
                    <h2>See what an index does</h2>
                    <ol>
                        {steps.map((s, i) => (
                            <li key={s.title} className={s.done ? "step done" : "step"}>
                                <span className="step-num">{s.done ? "✓" : i + 1}</span>
                                <div className="step-body">
                                    <b>{s.title}</b>
                                    <div className="hint">{s.text}</div>
                                </div>
                                <button onClick={s.action} disabled={running || s.disabled}>{s.button}</button>
                            </li>
                        ))}
                    </ol>
                </section>

                <details className="own-sql">
                    <summary>Write your own SQL</summary>
                    <textarea
                        className="sql"
                        value={sql}
                        onChange={(e) => setSql(e.target.value)}
                        onKeyDown={onKeyDown}
                        rows={4}
                        spellCheck={false}
                    />
                    <div className="actions">
                        <button className="primary" onClick={() => run()} disabled={running}>
                            {running ? "Running..." : "Run"}
                        </button>
                        <button onClick={() => compare()} disabled={running}>Compare scan vs index</button>
                        <span className="hint">Ctrl+Enter · CREATE TABLE, INSERT, SELECT ... WHERE, DELETE, CREATE INDEX</span>
                    </div>
                </details>

                <div ref={resultsRef} style={{ scrollMarginTop: 12 }}>
                    {error && <div className="error">{error}</div>}
                    {comparison && <CompareView scan={comparison.scan} index={comparison.index} />}
                    {result && <ResultView result={result} />}
                </div>

                <div ref={treeRef} style={{ scrollMarginTop: 12 }}>
                    {treeError && <div className="error">{treeError}</div>}
                    {tree && (
                        <>
                            <TreeView tree={tree} />
                            <button className="link" onClick={() => setTreeTarget(null)}>Hide tree</button>
                        </>
                    )}
                </div>
            </main>
        </div>
    );
}

/** One little square per table page. `touched` of them are lit. */
function PageMap({ total, touched, tone }) {
    const shown = Math.min(total, 300);
    const lit = Math.min(touched, shown);
    return (
        <div className="page-map" title={`${touched} of ${total} pages`}>
            {Array.from({ length: shown }, (_, i) => (
                <span key={i} className={i < lit ? `pg ${tone}` : "pg"} />
            ))}
        </div>
    );
}

function CompareView({ scan, index }) {
    const sameTable = scan.plan === index.plan;
    if (sameTable) {
        return (
            <section className="hero">
                <p className="hint">
                    No index can answer this query (it needs <b>col = value</b> on an indexed column), so both runs used: {scan.plan}.
                </p>
            </section>
        );
    }
    const total = scan.pagesRead;
    return (
        <section className="hero">
            <div className="hero-numbers">
                <span className="hero-from">{scan.pagesRead}</span>
                <span className="hero-arrow">→</span>
                <span className="hero-to">{index.pagesRead}</span>
                <span className="hero-unit">pages read</span>
            </div>
            <p className="verdict">
                The index read <b>{(scan.pagesRead / index.pagesRead).toFixed(1)}x fewer pages</b>
                {" "}and was <b>{(scan.elapsedMs / index.elapsedMs).toFixed(1)}x faster</b>.
            </p>

            <div className="maps">
                <div className="map-col">
                    <b>{scan.plan}</b>
                    <div className="hint">{scan.elapsedMs.toFixed(3)} ms · every page is read</div>
                    <PageMap total={total} touched={scan.pagesRead} tone="scan" />
                </div>
                <div className="map-col">
                    <b>{index.plan}</b>
                    <div className="hint">{index.elapsedMs.toFixed(3)} ms · only a few pages are read</div>
                    <PageMap total={total} touched={index.pagesRead} tone="index" />
                </div>
            </div>
            <p className="hint">Each square is one 4 KB page of the table.</p>
        </section>
    );
}

function ResultView({ result }) {
    const isQuery = result.columns.length > 0;
    const shown = result.rows.slice(0, MAX_SHOWN);
    return (
        <section className="result">
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