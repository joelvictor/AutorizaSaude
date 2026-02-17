const metrics = [
  { label: "Autorizacoes Hoje", value: "1.284" },
  { label: "Sucesso Tipo A", value: "98.2%" },
  { label: "Sucesso Tipo B", value: "92.7%" },
  { label: "DLQ Ativa", value: "14" }
];

const timeline = [
  "EVT-003 emitido para AUTH-9d5f (Bradesco)",
  "EVT-007 ACK recebido AUTH-4b21 (SulAmerica)",
  "EVT-013 falha tecnica AUTH-7a3e (Mais Saude)",
  "EVT-012 retry agendado AUTH-7a3e +45s"
];

export default function Home() {
  return (
    <main className="container">
      <header className="header">
        <div>
          <h1>TISS Hub</h1>
          <span className="tag">Fase 1 - Operacoes Multi-Operadora</span>
        </div>
        <strong>Tenant: Demo Hospital</strong>
      </header>

      <section className="grid">
        {metrics.map((item) => (
          <article className="card" key={item.label}>
            <small>{item.label}</small>
            <div className="metric">{item.value}</div>
          </article>
        ))}
      </section>

      <section className="card timeline">
        <h2>Timeline de Eventos</h2>
        <ul>
          {timeline.map((event) => (
            <li key={event} className={event.includes("falha") ? "warn" : ""}>
              {event}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
