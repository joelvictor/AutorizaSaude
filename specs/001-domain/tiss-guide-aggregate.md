# TissGuide Aggregate

## Entidade Raiz

- `TissGuide`

## Responsabilidades

- Gerar XML TISS por tipo de guia.
- Validar XML contra XSD correspondente.
- Manter versao do layout TISS aplicado.

## Invariantes

- XML deve ser serializavel e validado com sucesso antes de dispatch.
- Uma versao de layout TISS deve ser explicitamente registrada.
- Falha de validacao bloqueia envio e gera evento tecnico.

## Saidas

- `xml_content`
- `xml_hash`
- `validation_report`
