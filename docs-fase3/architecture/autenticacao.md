# Fluxo de autenticação por CPF

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant G as API Gateway
    participant L as Lambda Login CPF
    participant D as RDS PostgreSQL
    participant A as Lambda Authorizer
    participant B as Backend no EKS

    Cliente->>G: POST /auth/cpf com CPF
    G->>L: Encaminha a solicitação
    L->>L: Normaliza e valida os dígitos
    L->>D: Consulta cliente e situação
    D-->>L: Cadastro encontrado ou não
    alt cliente ativo
        L-->>Cliente: JWT assimétrico de curta duração
    else inválido, inexistente ou inativo
        L-->>Cliente: 401 com resposta genérica
    end
    Cliente->>G: API protegida + Bearer JWT
    G->>A: Validar token
    A-->>G: Allow ou Deny
    G->>B: Requisição autorizada
    B-->>Cliente: Resposta da API
```

## Controles de segurança

- CPF mascarado nos logs.
- Resposta genérica para evitar enumeração de clientes.
- JWT RSA de curta duração.
- Validação de assinatura, emissor, audiência e expiração.
- Rotas administrativas continuam restritas aos perfis internos.
