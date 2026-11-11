# Arquitetura geral

## Objetivo

Separar autenticação, aplicação, Kubernetes e banco gerenciado em unidades independentes de código e entrega, mantendo integração por contratos explícitos.

```mermaid
flowchart TB
    User[Cliente ou operador]
    Gateway[Amazon API Gateway]
    Login[Lambda Login CPF]
    Authorizer[Lambda Authorizer]
    LB[Load Balancer interno]
    App[Spring Boot no Amazon EKS]
    DB[(Amazon RDS PostgreSQL)]
    NotificationApi[Lambda de ingresso de notificações]
    Topic[Amazon SNS]
    NotificationWorker[Lambda de entrega]
    SES[Amazon SES]
    NR[New Relic]
    GH[GitHub Actions e GHCR]
    TF[HCP Terraform]

    User --> Gateway
    Gateway --> Login
    Gateway --> Authorizer
    Authorizer --> Gateway
    Gateway --> LB
    LB --> App
    Login --> DB
    App --> DB
    App --> NotificationApi
    NotificationApi --> Topic
    Topic --> NotificationWorker
    NotificationWorker --> SES
    Login --> NR
    App --> NR
    Gateway --> NR
    App -. imagem .-> GH
    TF --> DB
    TF --> App
```

## Fluxos principais

1. O cliente envia o CPF ao endpoint público de autenticação.
2. A Lambda valida o documento e consulta a existência e o status do cliente.
3. Um JWT de curta duração é devolvido ao cliente.
4. O token acompanha as chamadas protegidas pelo API Gateway.
5. O autorizador valida assinatura, emissor, audiência e expiração.
6. O API Gateway encaminha a chamada autorizada para a aplicação no EKS.
7. Mudanças relevantes da OS são aceitas por uma Lambda autenticada e publicadas no SNS.
8. Uma Lambda consumidora entrega o e-mail pelo SES sem bloquear a transação da OS.
9. Aplicação, Lambdas, Kubernetes, API Gateway e RDS enviam telemetria ao New Relic.

## Princípios

- um repositório por unidade independente de entrega;
- nenhum módulo compartilhado por cópia de código;
- contratos versionados para integração;
- infraestrutura e estados Terraform separados;
- menor privilégio possível dentro das limitações do AWS Academy;
- nenhum CPF completo em logs, traces ou métricas;
- mudanças na `main` somente por Pull Request.

## Ambientes

| Ambiente lógico | Branch | Finalidade |
|---|---|---|
| Homologação | `homolog` | integração e demonstração antes da entrega |
| Produção | `main` | versão estável aprovada |

Os ambientes utilizam a mesma conta temporária do AWS Academy e devem ser recriáveis por pipeline. Os nomes dos recursos e os estados Terraform permanecem separados.
