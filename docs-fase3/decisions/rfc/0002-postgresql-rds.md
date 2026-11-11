# RFC 0002 — PostgreSQL gerenciado no Amazon RDS

- **Status:** aprovada e implementada

## Contexto

Clientes, veículos, ordens, orçamentos, estoque e financeiro formam um domínio transacional com múltiplos relacionamentos.

## Decisão

Manter PostgreSQL 16 no Amazon RDS, em sub-redes privadas, com schema evoluído por migrations Flyway do Backend.

## Motivos

- Transações ACID e integridade referencial.
- FKs, constraints, índices parciais e colunas geradas.
- Compatibilidade com o modelo já existente.
- Backups, métricas e logs providos por serviço gerenciado.

## Alternativas rejeitadas

- DynamoDB: exigiria remodelagem e consistência na aplicação.
- PostgreSQL no EKS: aumentaria a responsabilidade operacional.
- Aurora Serverless: custo e restrições do ambiente acadêmico.

## Referência

O modelo completo está em [Database docs](https://github.com/tiagomiele/database).
