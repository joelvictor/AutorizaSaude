# OperatorDispatch Aggregate

## Entidade Raiz

- `OperatorDispatch`

## Responsabilidades

- Resolver estrategia por operadora (`Tipo A`, `Tipo B`, `Tipo C`).
- Controlar estado tecnico de envio e consulta.
- Capturar protocolo externo e latencia de round-trip.

## Estados Tecnicos

- `READY`
- `SENT`
- `ACK_RECEIVED`
- `POLLING`
- `COMPLETED`
- `TECHNICAL_ERROR`
- `DLQ`

## Invariantes

- Toda tentativa de envio incrementa contador de tentativa.
- Falhas tecnicas acionam politica de retry/circuit breaker.
- Exaustao de tentativas move evento para dead letter.

## Mapeamento Inicial de Tipo por Operadora (Fase 1)

- `Tipo A`: Bradesco, SulAmerica, Amil, Porto Seguro, Omint.
- `Tipo B`: Unimed Anapolis, Allianz Saude, Care Plus, Mediservice.
- `Tipo C`: Assim, Golden Cross, Prevent Senior, BioVida, Health Smart, Oncomed, Mais Saude, Hapvida, Ipasgo, NotreDame Intermedica, Allianz Care.

> Observacao: classificacao inicial deve ser validada em discovery tecnico com cada operadora.
