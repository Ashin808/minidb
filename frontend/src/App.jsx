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

    // what the guide needs to know, taken from the server's table list
    const demo = tables.find((t) => t.name === "demo");
    const hasData = !!demo && demo.pages >= 10;
    const hasIndex = !!demo && demo.indexes.some((ix) => ix.column === "id");

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

    // redraw the tree whenever the chosen index changes or the tables list refreshes (after every query)
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

    // bring a new result into view, so a guide button visibly does something
    useEffect(() => {
        if (result || comparison || error) {
            resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    }, [result, comparison, error]);

    // scroll to the tree only right after the user asked for it (not on every refresh)
    useEffect(() => {
        if (tree && scrollToTreeNext.current) {
            scrollToTreeNext.current = false;
            treeRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    }, [tree]);

    // text is optional: guide buttons pass their own SQL, the Run button uses the box. Returns true on success.
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
            refreshTables();   // CREATE TABLE / CREATE INDEX / INSERT change what the sidebar shows
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
            text: "Creates a table called demo and fills it. Data is stored in real 4 KB pages on disk.",
            button: hasData ? "Done" : "Add 5000 rows",
            done: hasData,
            disabled: hasData,
            action: () => seed(5000),
        },
        {
            title: "Find one row without an index",
            text: "The engine has to read every page of the table to find id 4000. Note the pages read.",
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
            text: "Runs the same query both ways and shows pages read and time side by side.",
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
                <p className="hint">Adds rows to table <b>demo</b> (created if missing). Max 20000.</p>
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
                    <h2>Try it in 5 steps</h2>
                    <p className="hint">See how an index turns a full table scan into a few page reads.</p>
                    <ol>
                        {steps.map((s, i) => (
                            <li key={s.title} className={s.done ? "step done" : "step"}>
                                <div className="step-body">
                                    <b>{s.done ? "✓ " : ""}{i + 1}. {s.title}</b>
                                    <div className="hint">{s.text}</div>
                                </div>
                                <button onClick={s.action} disabled={running || s.disabled}>{s.button}</button>
                            </li>
                        ))}
                    </ol>
                </section>

                <h2 className="section-title">Or write your own SQL</h2>
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
                    <span className="hint">Ctrl+Enter · supports CREATE TABLE, INSERT, SELECT ... WHERE, DELETE, CREATE INDEX</span>
                </div>

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
                <>
                    <div className="compare-grid">
                        <div className="card">
                            <b>{scan.plan}</b>
                            <div className="big">{scan.pagesRead} pages</div>
                            <div>time: {scan.elapsedMs.toFixed(3)} ms</div>
                        </div>
                        <div className="card good">
                            <b>{index.plan}</b>
                            <div className="big">{index.pagesRead} pages</div>
                            <div>time: {index.elapsedMs.toFixed(3)} ms</div>
                        </div>
                    </div>
                    <p className="verdict">
                        The index read <b>{(scan.pagesRead / index.pagesRead).toFixed(1)}x fewer pages</b>
                        {" "}and was <b>{(scan.elapsedMs / index.elapsedMs).toFixed(1)}x faster</b>.
                    </p>
                </>
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