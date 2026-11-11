# ADR 0004 — Logs estruturados, correlação e traces

- **Status:** aceita

## Contexto

Uma requisição atravessa API Gateway, Lambda Authorizer, Backend, banco e, em alguns fluxos, notificações assíncronas. Logs isolados dificultam o diagnóstico.

## Decisão

- emitir logs estruturados em JSON;
- propagar `correlationId` ou `traceId` entre componentes;
- instrumentar aplicação e funções para tracing;
- mascarar CPF, JWT, credenciais e dados sensíveis;
- consultar logs e traces de forma centralizada no New Relic.

## Consequências

Falhas podem ser acompanhadas ponta a ponta e relacionadas às métricas da aplicação. Novos componentes devem preservar o identificador recebido ou criar um quando ausente.
