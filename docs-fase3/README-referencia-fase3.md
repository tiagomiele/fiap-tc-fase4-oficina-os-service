# Referência da Fase 3 — Oficina

Este arquivo é uma base de consulta rápida à Fase 3. O projeto completo, com o código, o README central, as decisões e as evidências, está em:

**[fiap-tech-challenge-fase3-oficina-backend](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend)**

## O que foi a Fase 3

- **Backend:** monólito modular em Java 21 e Spring Boot 3.3, com DDD e Clean Architecture em 4 anéis (`Domain`, `Usecase`, `Adapter` e `Infrastructure`).
- **Entrada e autenticação:** Amazon API Gateway e uma Lambda que valida o CPF do cliente e emite um JWT RSA de curta duração. Um Lambda Authorizer protege as rotas do cliente.
- **Dados:** 1 Amazon RDS PostgreSQL 16, com as migrations Flyway V1 a V4.
- **Plataforma:** Amazon EKS com HPA, PDB e Metrics Server.
- **Entrega:** GitHub Actions, GHCR e HCP Terraform, com os ambientes `homolog` e `production`.
- **Observabilidade:** New Relic, com APM, logs JSON correlacionados, traces, dashboards e alertas.
- **Repositórios:** a solução foi dividida em 4 repositórios independentes: Backend, Auth, Database e Kubernetes.

Ciclo da OS na Fase 3:

```text
Recebida → Em diagnóstico → Aguardando aprovação → Em execução
→ Aguardando pagamento → Paga → Entregue
```

## Onde consultar

- [README central da Fase 3](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/blob/main/README.md)
- [Índice de evidências da Fase 3](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/blob/main/README-Indice-Evidencia-fase3.md)
- [Requisitos obrigatórios da Fase 3](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/blob/main/README-requisitos-obrigatorios-fase3.md)
- [Arquitetura](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/tree/main/docs-fase3/architecture)
- [ADRs e RFCs](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/tree/main/docs-fase3/decisions)
- [Evidências](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/tree/main/docs-fase3/evidencias)
- [Bootstrap da AWS do zero](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/blob/main/docs-fase3/bootstrap-aws-do-zero.md)

## Projetos da Fase 3

- [Backend](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend)
- [Auth Serverless](https://github.com/tiagomiele/fiap-tech-challenge-fase3-fiap-tc-fase4-oficina-auth-serverless)
- [Database Infra](https://github.com/tiagomiele/fiap-tech-challenge-fase3-fiap-tc-fase4-oficina-database-infra)
- [Kubernetes Infra](https://github.com/tiagomiele/fiap-tech-challenge-fase3-fiap-tc-fase4-oficina-kubernetes-infra)

## Relação com a Fase 4

Na Fase 4, o monólito da Fase 3 foi dividido em 3 microsserviços, cada um com o próprio banco: OS, Orçamento e Pagamento, e Diagnóstico e Reparo. A comunicação entre eles passou a ser uma Saga orquestrada via SQS. A pasta `docs-fase3` deste repositório guarda a cópia da documentação da Fase 3 que serviu de base para essa evolução.
