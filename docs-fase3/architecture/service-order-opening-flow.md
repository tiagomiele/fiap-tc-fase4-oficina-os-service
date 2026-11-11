# Sequência de abertura da Ordem de Serviço

```mermaid
sequenceDiagram
    autonumber
    actor Operador
    participant API as Backend Spring Boot
    participant Security as Spring Security
    participant Domain as OrdemServicoService
    participant DB as RDS PostgreSQL
    participant Obs as New Relic
    participant Notification as API serverless de notificação
    participant SNS as Amazon SNS
    participant Worker as Lambda de entrega
    participant SES as Amazon SES

    Operador->>API: POST /ordens-servico + JWT + X-Request-Id
    API->>Security: Validar assinatura, emissor, audiência e perfil
    Security-->>API: Identidade autorizada
    API->>Domain: Abrir OS
    Domain->>Domain: Validar cliente, veículo e dados obrigatórios
    Domain->>DB: Persistir OS RECEBIDA e histórico inicial
    DB-->>Domain: Número da OS e timestamps
    Domain->>Obs: OficinaOrdemServico criada
    Domain->>Notification: POST notificação + chave técnica
    Notification-->>Domain: 202 Accepted
    Notification->>SNS: Publicar evento sanitizado
    SNS-->>Worker: Invocação assíncrona com retry e DLQ
    Worker->>SES: Enviar e-mail ao cliente
    Worker->>Obs: Sucesso ou falha técnica
    Domain-->>API: OS criada
    API-->>Operador: 201 Created + número da OS + X-Request-Id
```

## Garantias

- A autenticação e autorização ocorrem antes da execução do caso de uso.
- A OS e seu histórico inicial são gravados na mesma transação.
- O `X-Request-Id` é preservado nos logs e eventos de observabilidade.
- A indisponibilidade da notificação não desfaz a OS já criada; a falha é registrada tecnicamente.
- O SNS executa a entrega assíncrona e encaminha falhas definitivas para a DLQ.
- CPF, credenciais e conteúdo integral da mensagem não são enviados a logs ou métricas.
