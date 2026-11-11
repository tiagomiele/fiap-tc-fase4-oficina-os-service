# ADR 0005 — Alta disponibilidade da aplicação no Kubernetes

- **Status:** aceito
- **Data:** 2026-05-24

## Decisão

A aplicação executa com no mínimo duas réplicas, rolling update sem indisponibilidade, HPA por CPU e memória, probes de startup/readiness/liveness e distribuição por hostname e zona de disponibilidade. Um PodDisruptionBudget preserva pelo menos uma réplica durante interrupções voluntárias.

A restrição por hostname é obrigatória; a distribuição por zona usa `ScheduleAnyway` para continuar compatível com ambientes temporários que momentaneamente possuam capacidade em apenas uma zona.

## Consequências

- manutenção voluntária não deve remover todas as réplicas simultaneamente;
- o scheduler evita concentrar as duas réplicas no mesmo node;
- o HPA pode aumentar a aplicação de duas para cinco réplicas;
- a disponibilidade completa também depende de nodes em múltiplas zonas e do perfil Multi-AZ do RDS em produção.
