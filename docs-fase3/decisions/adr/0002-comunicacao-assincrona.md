# ADR 0002 — Comunicação assíncrona para notificações

- **Status:** aceita

## Decisão

O Backend envia uma solicitação técnica para uma Lambda de ingresso, que publica um evento sanitizado no SNS. Uma Lambda consumidora realiza a entrega, com retry e SQS DLQ.

## Motivos

- A criação da OS não depende da entrega da notificação.
- Falhas podem ser reprocessadas.
- SNS permite evolução para novos consumidores.
- Dados sensíveis não são propagados no evento.

## Consequência

A operação é eventualmente consistente e exige correlação, métricas e alertas.
