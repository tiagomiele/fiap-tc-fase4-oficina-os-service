# RFC 0004 — Observabilidade com New Relic

- **Status:** aprovada e implementada

## Contexto

A solução distribuída precisa correlacionar APIs, Lambdas, EKS, RDS e notificações, além de demonstrar saúde e desempenho durante a avaliação.

## Decisão

Utilizar New Relic como plataforma central de APM, infraestrutura Kubernetes, logs, traces, dashboards, alertas e monitor sintético.

## Motivos

- visão integrada dos componentes;
- suporte a APM Java e workloads serverless;
- métricas de CPU e memória do EKS;
- consultas NRQL para indicadores das ordens de serviço;
- infraestrutura de dashboards e alertas gerenciada por Terraform.

## Alternativas consideradas

- CloudWatch isolado: útil na AWS, mas exigiria maior consolidação manual.
- Datadog: atende tecnicamente, mas não foi a ferramenta adotada pelo projeto.
- Prometheus e Grafana próprios: aumentariam a operação dentro do ambiente acadêmico.

## Consequências

A instrumentação deve evitar PII, preservar correlação e manter dashboards alinhados aos eventos publicados pela aplicação.
