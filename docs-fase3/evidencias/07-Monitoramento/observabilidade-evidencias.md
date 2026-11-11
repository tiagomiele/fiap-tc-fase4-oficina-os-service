# Observabilidade e evidências

## Objetivo

Comprovar que a solução permite acompanhar disponibilidade, desempenho e falhas durante a passagem de uma requisição pelos componentes.

## Cobertura esperada no New Relic

| Área | Evidência esperada |
|---|---|
| APIs | Latência, throughput, erros HTTP e disponibilidade |
| Kubernetes | CPU, memória, nós, pods, namespace e HPA |
| Saúde | Startup, liveness, readiness e monitor sintético |
| Ordens de serviço | Volume diário e falhas de processamento |
| Etapas da OS | Tempo médio em diagnóstico, execução e finalização |
| Integrações | Falhas em API Gateway, Lambda, RDS e notificações |
| Correlação | Logs JSON com `correlationId` ou `traceId` |
| Tracing | Caminho da requisição entre API Gateway, Lambda e Backend |

## Links para evidências após a execução

Preencher somente depois de reconstruir e validar o ambiente:

- [New Relic — consumo de CPU](docs-fase3/evidencias/07-Monitoramento/NR-CPU.png)
- [New Relic — disponibilidade da aplicação](docs-fase3/evidencias/07-Monitoramento/NR-Disponibilidade.png)
- [New Relic — falhas nas ordens de serviço](docs-fase3/evidencias/07-Monitoramento/NR-Falhas-OS.png)
- [New Relic — latência das APIs](docs-fase3/evidencias/07-Monitoramento/NR-latencia.png)
- [New Relic — logs da aplicação](docs-fase3/evidencias/07-Monitoramento/NR-Logs.png)
- [New Relic — tempo médio por status da ordem de serviço](docs-fase3/evidencias/07-Monitoramento/NR-Media-Status-OS.png)
- [New Relic — volume diário de ordens de serviço](docs-fase3/evidencias/07-Monitoramento/NR-Vol-OS-dia.png)
- [Vídeo auxiliar — dashboards, logs e traces](https://vimeo.com/1226611174) — duração informada: 4min08s.

## Referências

- [Infraestrutura e observabilidade](https://github.com/tiagomiele/kubernetes/blob/documentation/docs/infraestrutura-observabilidade.md)
- [RFC de observabilidade](../../decisions/rfc/0004-observabilidade-new-relic.md)
- [ADR de logs e correlação](../../decisions/adr/0004-logs-correlacao-traces.md)
