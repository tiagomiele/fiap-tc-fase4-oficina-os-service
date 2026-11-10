# ADR 0003 — Alta disponibilidade e HPA

- **Status:** aceita

## Decisão

Executar o Backend no EKS com pelo menos duas réplicas, rolling update, probes, HPA por CPU e memória, PodDisruptionBudget e distribuição por nó e zona.

## Motivos

- Reduzir indisponibilidade durante falhas e deploys.
- Escalar conforme consumo real.
- Evitar concentração de réplicas no mesmo nó.

## Consequências

O Metrics Server é obrigatório. Requests e limits precisam estar definidos para o HPA calcular utilização corretamente.
