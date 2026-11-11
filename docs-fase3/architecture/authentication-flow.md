# Autenticação serverless por CPF

## Fluxo

```mermaid
sequenceDiagram
    actor Cliente
    participant G as API Gateway
    participant L as Lambda Login CPF
    participant D as RDS PostgreSQL
    participant A as Lambda Authorizer
    participant B as Backend no EKS

    Cliente->>G: POST /auth/cpf
    G->>L: Corpo JSON com CPF
    L->>L: Validar dígitos verificadores
    L->>D: Consultar cliente por CPF
    D-->>L: Cliente e status
    alt cliente ativo
        L-->>Cliente: JWT de curta duração
    else inválido, inexistente ou inativo
        L-->>Cliente: Resposta genérica 401
    end
    Cliente->>G: API protegida + Bearer JWT
    G->>A: Autorizar token
    A-->>G: Allow ou Deny
    G->>B: Requisição autorizada
    B-->>Cliente: Resposta da API
```

## Regras

- aceitar CPF com ou sem máscara e persistir/consultar somente o valor normalizado;
- validar formato e dígitos verificadores antes de consultar o banco;
- responder de forma genérica para evitar enumeração de clientes;
- emitir tokens com `sub`, `client_id`, `role`, `iss`, `aud`, `iat`, `exp` e `jti`;
- não incluir CPF completo no token;
- usar expiração curta e assinatura assimétrica;
- armazenar a chave privada fora do código;
- validar token no API Gateway e manter defesa adicional no backend;
- não registrar CPF ou token em logs e eventos.

## Acesso ao banco

A Lambda de login executa nas subnets privadas, reutiliza o security group autorizado no RDS e consulta o PostgreSQL por JDBC. RDS Proxy poderá ser avaliado depois conforme permissões e custos do AWS Academy.

## Swagger unificado

O Swagger do Backend documenta os dois emissores de token. A operação `POST /auth/cpf` possui um servidor OpenAPI próprio, configurado por `AUTH_BASE_URL`, e o navegador chama diretamente o API Gateway. Funcionários e técnicos usam `POST /auth/login` no servidor do Backend. O perfil é obtido do cadastro autenticado e não pode ser escolhido no request.

## Contrato

O contrato inicial está em [authentication-api.yaml](../contracts/authentication-api.yaml). Alterações incompatíveis exigem nova versão do endpoint e atualização do RFC correspondente.
