import { useState } from "react";
import { runQuery } from "./api";
import "./App.css";

const EXAMPLE = "SELECT * FROM users WHERE id = 2";

export default function App() {
  const [sql, setSql] = useState(EXAMPLE);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const [running, setRunning] = useState(false);

  async function run() {
    if (running || !sql.trim()) return;
    setRunning(true);
    setError("");
    try {
      setResult(await runQuery(sql));
    } catch (e) {
      setResult(null);
      setError(e.message);
    } finally {
      setRunning(false);
    }
  }

  function onKeyDown(e) {
    if (e.ctrlKey && e.key === "Enter") run();
  }

  return (
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
          <span className="hint">Ctrl+Enter</span>
        </div>

        {error && <div className="error">{error}</div>}
        {result && <ResultView result={result} />}
      </main>
  );
}

function ResultView({ result }) {
  const isQuery = result.columns.length > 0;
  return (
      <section>
        <p className="message">{result.message}</p>

        {isQuery && result.rows.length > 0 && (
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
                {result.rows.map((row, i) => (
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

        <div className="stats">
          {result.plan && <span>plan: <b>{result.plan}</b></span>}
          {result.plan && <span>pages read: <b>{result.pagesRead}</b></span>}
          <span>time: <b>{result.elapsedMs.toFixed(3)} ms</b></span>
        </div>
      </section>
  );
}