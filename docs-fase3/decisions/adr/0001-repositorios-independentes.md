# ADR 0001 — Quatro repositórios independentes

- **Status:** aceita

## Decisão

Separar Backend, Auth serverless, infraestrutura Kubernetes e infraestrutura Database em quatro repositórios.

## Motivos

- Ciclos de mudança e deploy independentes.
- Permissões e segredos isolados.
- Estados Terraform separados.
- Responsabilidades claras.

## Consequência

Outputs precisam ser sincronizados na ordem Kubernetes → Database → Auth → Backend.
