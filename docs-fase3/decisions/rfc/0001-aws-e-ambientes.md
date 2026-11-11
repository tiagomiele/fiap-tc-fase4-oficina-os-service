# RFC 0001 — AWS e separação de ambientes

- **Status:** aprovada e implementada

## Contexto

A solução precisa executar aplicação Kubernetes, banco gerenciado, autenticação serverless e observabilidade, mantendo homologação e produção isoladas.

## Decisão

Utilizar AWS Academy com Amazon EKS, RDS PostgreSQL, API Gateway, Lambda, SNS, SQS e SES opcional. Terraform e HCP Terraform gerenciam infraestrutura e estados. `homolog` representa homologação e `main` representa produção.

## Motivos

- Serviços gerenciados reduzem esforço operacional.
- EKS permite escalabilidade e alta disponibilidade.
- Lambda atende autenticação e notificações serverless.
- Workspaces e GitHub Environments isolam configurações.

## Consequências

As credenciais do Learner Lab expiram, os recursos podem ser temporários e a sequência de provisionamento precisa ser respeitada.
