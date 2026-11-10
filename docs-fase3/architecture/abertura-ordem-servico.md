# Fluxo de abertura de ordem de serviço

```mermaid
sequenceDiagram
    autonumber
    actor Operador
    participant G as API Gateway
    participant A as Lambda Authorizer
    participant B as Backend no EKS
    participant D as RDS PostgreSQL
    participant N as New Relic
    participant I as Lambda de ingresso
    participant S as Amazon SNS
    participant W as Lambda de entrega

    Operador->>G: POST /ordens-servico + JWT
    G->>A: Autorizar identidade e perfil
    A-->>G: Allow
    G->>B: Encaminhar requisição
    B->>B: Validar cliente, veículo e dados
    B->>D: Persistir OS RECEBIDA e histórico inicial
    D-->>B: Número da OS e timestamps
    B->>N: Registrar evento e correlação
    B->>I: Solicitar notificação
    I-->>B: 202 Accepted
    I->>S: Publicar evento sanitizado
    S-->>W: Processar com retry e DLQ
    W->>N: Registrar sucesso ou falha
    B-->>Operador: 201 Created + número da OS
```

## Resultado

A OS é persistida de forma transacional. A notificação é desacoplada do fluxo principal: indisponibilidade da entrega não desfaz a criação da ordem de serviço.
