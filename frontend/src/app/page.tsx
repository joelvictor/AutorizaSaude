type ObservabilitySummaryResponse = {
  latencyP95ByOperator: Array<{
    operatorCode: string;
    p95LatencyMs: number;
    samples: number;
  }>;
  successRateByOperatorAndType: Array<{
    operatorCode: string;
    dispatchType: string;
    successful: number;
    total: number;
    successRate: number;
  }>;
  queues: {
    dispatchRetryQueue: number;
    dispatchDeadLetter: number;
    outboxRetryQueue: number;
    outboxDeadLetter: number;
  };
};

type OutboxStatsResponse = {
  pending: number;
  published: number;
  deadLetter: number;
};

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const tenantId = process.env.NEXT_PUBLIC_TENANT_ID ?? "11111111-1111-1111-1111-111111111111";

async function fetchJson<T>(path: string, headers: HeadersInit = {}): Promise<T | null> {
  try {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      headers,
      cache: "no-store"
    });
    if (!response.ok) {
      return null;
    }
    return (await response.json()) as T;
  } catch {
    return null;
  }
}

export default async function Home() {
  const [summary, outboxStats] = await Promise.all([
    fetchJson<ObservabilitySummaryResponse>("/v1/operations/observability/summary", {
      "X-Tenant-Id": tenantId
    }),
    fetchJson<OutboxStatsResponse>("/v1/operations/outbox")
  ]);

  const cards = [
    {
      label: "Dispatch Retry Queue",
      value: summary ? String(summary.queues.dispatchRetryQueue) : "-",
      tone: "info"
    },
    {
      label: "Dispatch DLQ",
      value: summary ? String(summary.queues.dispatchDeadLetter) : "-",
      tone: "warn"
    },
    {
      label: "Outbox Pending",
      value: outboxStats ? String(outboxStats.pending) : "-",
      tone: "info"
    },
    {
      label: "Outbox Dead Letter",
      value: summary ? String(summary.queues.outboxDeadLetter) : "-",
      tone: "warn"
    }
  ];

  return (
    <main className="container">
      <header className="header">
        <div>
          <h1>TISS Hub Operations</h1>
          <span className="tag">Observabilidade ativa por tenant</span>
        </div>
        <strong className="tenant">Tenant: {tenantId}</strong>
      </header>

      <section className="grid">
        {cards.map((item) => (
          <article className={`card ${item.tone}`} key={item.label}>
            <small>{item.label}</small>
            <div className="metric">{item.value}</div>
          </article>
        ))}
      </section>

      <section className="two-cols">
        <article className="card panel">
          <h2>Latency p95 por Operadora</h2>
          {!summary || summary.latencyP95ByOperator.length === 0 ? (
            <p className="muted">Sem dados de latencia disponiveis para este tenant.</p>
          ) : (
            <ul className="list">
              {summary.latencyP95ByOperator.map((item) => (
                <li key={item.operatorCode}>
                  <span>{item.operatorCode}</span>
                  <strong>{item.p95LatencyMs} ms</strong>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="card panel">
          <h2>Taxa de Sucesso por Tipo</h2>
          {!summary || summary.successRateByOperatorAndType.length === 0 ? (
            <p className="muted">Sem dados de sucesso disponiveis para este tenant.</p>
          ) : (
            <ul className="list">
              {summary.successRateByOperatorAndType.map((item) => (
                <li key={`${item.operatorCode}-${item.dispatchType}`}>
                  <span>
                    {item.operatorCode} / {item.dispatchType}
                  </span>
                  <strong>
                    {item.successRate.toFixed(2)}% ({item.successful}/{item.total})
                  </strong>
                </li>
              ))}
            </ul>
          )}
        </article>
      </section>
    </main>
  );
}
