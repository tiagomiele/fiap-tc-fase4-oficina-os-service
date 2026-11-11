# API backend da oficina mecânica desenvolvida com arquitetura monolítica (em camadas)

- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json` (requisições e respostas)
- **Swagger UI interativo**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

## Sumário

| Contexto | Seção |
|---|---|
| Convenções gerais (auth, erros, paginação) | [Convenções](#convenções) |
| Identity — Autenticação | [1. Autenticação](#1-autenticação) |
| Identity — Usuários | [2. Usuários](#2-usuários) |
| Clientes | [3. Clientes](#3-clientes) |
| Veículos | [4. Veículos](#4-veículos) |
| Catálogo — Serviços | [5. Serviços](#5-serviços) |
| Catálogo — Peças | [6. Peças](#6-peças) |
| Ordens de Serviço — Admin | [7. Ordens de Serviço (administrativo)](#7-ordens-de-serviço-administrativo) |
| Ordens de Serviço — Cliente | [8. Ordens de Serviço (público / cliente)](#8-ordens-de-serviço-público--cliente) |
| Relatórios | [9. Relatórios](#9-relatórios) |
| Códigos de erro | [Apêndice A — Códigos de erro](#apêndice-a--códigos-de-erro) |
| Máquina de estados da OS | [Apêndice B — Máquina de estados da OS](#apêndice-b--máquina-de-estados-da-os) |

---

## Convenções

### Autenticação

- **Rotas administrativas** (`/api/v1/...` exceto `/api/v1/public/**`): exigem header `Authorization: Bearer <access_token>`.
- **Rotas públicas** (`/api/v1/public/**` e `/auth/**`): não exigem token.
- JWT HS256 com TTL **15 minutos** (access) e **7 dias** (refresh).
- Papéis existentes no token (`papel` claim):
  - `FUNCIONARIO_DA_OFICINA` → authority Spring `ROLE_FUNCIONARIO_DA_OFICINA`
  - `TECNICO_DA_OFICINA` → authority Spring `ROLE_TECNICO_DA_OFICINA`

### Padrão de erro (envelope único)

Todas as respostas de erro seguem este formato (`application/json`):

```json
{
  "code": "CUSTOMER_NOT_FOUND",
  "message": "Cliente não encontrado.",
  "timestamp": "2026-04-20T12:34:56.789Z",
  "path": "/api/v1/clientes/6a0c3a0b-...",
  "details": null
}
```

Quando é erro de validação (`400`), `details` vira uma lista:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Erro de validação nos dados enviados.",
  "timestamp": "2026-04-20T12:34:56.789Z",
  "path": "/api/v1/clientes",
  "details": [
    { "field": "documento", "message": "não deve estar em branco" },
    { "field": "nome", "message": "tamanho inválido" }
  ]
}
```

### Mapeamento de códigos de domínio → HTTP

| HTTP | Códigos de domínio típicos |
|---|---|
| `400 Bad Request` | `DOCUMENT_INVALID`, `PLATE_INVALID`, `MONEY_INVALID*`, `STOCK_INVALID`, `VALIDATION_ERROR` |
| `401 Unauthorized` | `AUTH_INVALID_CREDENTIALS` (credenciais inválidas), token ausente/expirado |
| `403 Forbidden` | `AUTH_FORBIDDEN` (papel sem permissão para a rota) |
| `404 Not Found` | `*_NOT_FOUND` (cliente, veículo, serviço, peça, OS, usuário) |
| `409 Conflict` | `*_DUPLICATED` (documento, placa, SKU, email) |
| `422 Unprocessable Entity` | `WO_INVALID_STATUS_TRANSITION`, `PART_OUT_OF_STOCK`, `BUDGET_NOT_APPROVED`, `WO_EMPTY_BUDGET`, `OS_INVALID_STATUS_TRANSITION` |
| `500 Internal Server Error` | `INTERNAL_ERROR` (inesperado) |

### Paginação

Endpoints de listagem aceitam query params:

- `page` — 0-based, default `0`
- `size` — default `20`

Não há envelope de paginação: a resposta é uma lista JSON simples (`[]`).

### Formato de datas

- Timestamps ISO-8601 com offset (`OffsetDateTime`): `"2026-04-20T14:23:11Z"`.
- OS número: `OS-<YYYY>-<NNNNNN>` (ex.: `OS-2026-000001`).

---

## 1. Autenticação

### `POST /auth/login`

Faz login administrativo. **Rota pública.**

**Request body**

| Campo | Tipo | Obrig. | Validação |
|---|---|---|---|
| `email` | string | sim | `@NotBlank`, `@Email` |
| `password` | string | sim | `@NotBlank` |

```json
{ "email": "admin@oficina.local", "password": "admin123" }
```

**Response `200 OK`**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-04-20T14:38:11Z"
}
```

**Respostas de erro**

| Status | `code` | Quando |
|---|---|---|
| 400 | `VALIDATION_ERROR` | email vazio/mal formatado, senha vazia |
| 401 | `AUTH_INVALID_CREDENTIALS` | usuário inexistente, senha incorreta, usuário inativo |

---

## 2. Usuários

### `POST /api/v1/usuarios`

Cria um novo usuário administrativo (funcionário da oficina ou técnico da oficina).

- **Auth**: requer `ROLE_FUNCIONARIO_DA_OFICINA`.

**Request body**

| Campo | Tipo | Obrig. | Validação |
|---|---|---|---|
| `email` | string | sim | `@NotBlank`, `@Email` |
| `senha` | string | sim | `@Size(min=8, max=72)` |
| `nome` | string | sim | `@NotBlank` |
| `papel` | enum | sim | `FUNCIONARIO_DA_OFICINA` ou `TECNICO_DA_OFICINA` |

```json
{
  "email": "joao@oficina.local",
  "senha": "senhaSegura123",
  "nome": "João da Silva",
  "papel": "TECNICO_DA_OFICINA"
}
```

**Response `201 Created`**

```json
{
  "id": "3f7a3a2c-3e4a-4c0f-9e2a-6b1c3d9e1a11",
  "email": "joao@oficina.local",
  "nome": "João da Silva",
  "papel": "TECNICO_DA_OFICINA"
}
```

**Respostas de erro**

| Status | `code` |
|---|---|
| 400 | `VALIDATION_ERROR` |
| 401 | token ausente/expirado |
| 403 | `AUTH_FORBIDDEN` (papel sem permissão) |
| 409 | `USER_EMAIL_DUPLICATED` |

---

## 3. Clientes

Base: `/api/v1/clientes` — todas exigem `ROLE_FUNCIONARIO_DA_OFICINA`.

### `POST /api/v1/clientes`

Cria um novo cliente.

**Request body**

| Campo | Tipo | Obrig. | Validação |
|---|---|---|---|
| `documento` | string | sim | `@NotBlank`; validado como CPF (11 dígitos + DV) ou CNPJ (14 dígitos + DV). Aceita máscara — é normalizado internamente para só dígitos. |
| `nome` | string | sim | 1..255 caracteres |
| `email` | string | não | `@Email` se informado |
| `telefone` | string | não | — |

```json
{
  "documento": "529.982.247-25",
  "nome": "Maria Souza",
  "email": "maria@exemplo.com",
  "telefone": "11999990000"
}
```

**Response `201 Created`**

```json
{
  "id": "c3d87d1a-a8e2-4d6e-a71c-1f5a9f8c0b14",
  "documento": "52998224725",
  "tipoDocumento": "CPF",
  "nome": "Maria Souza",
  "email": "maria@exemplo.com",
  "telefone": "11999990000",
  "ativo": true
}
```

**Erros**: `400 VALIDATION_ERROR`, `400 DOCUMENT_INVALID` (DV inválido), `409 CUSTOMER_DOCUMENT_DUPLICATED`.

### `GET /api/v1/clientes/{id}`

Busca um cliente por id (UUID).

- **`200 OK`**: mesmo shape da resposta acima.
- **`404 CUSTOMER_NOT_FOUND`** se não existe.

### `GET /api/v1/clientes?page={page}&size={size}`

Lista clientes.

- **`200 OK`**: `[ClienteResponse, ...]`

### `PUT /api/v1/clientes/{id}`

Atualiza dados de um cliente (documento **não** é alterado aqui).

**Request body**

| Campo | Tipo | Obrig. |
|---|---|---|
| `nome` | string | sim (`@NotBlank`) |
| `email` | string | não (`@Email` se informado) |
| `telefone` | string | não |

**Response `200 OK`**: `ClienteResponse`. **Erros**: `400 VALIDATION_ERROR`, `404 CUSTOMER_NOT_FOUND`.

### `DELETE /api/v1/clientes/{id}`

Desativa o cliente (soft delete — seta `ativo=false`).

- **Response `204 No Content`**.
- **Erros**: `404 CUSTOMER_NOT_FOUND`.

---

## 4. Veículos

Base: `/api/v1/veiculos` — todas exigem `ROLE_FUNCIONARIO_DA_OFICINA`.

### `POST /api/v1/veiculos`

Cria veículo vinculado a um cliente.

**Request body**

| Campo | Tipo | Obrig. | Validação |
|---|---|---|---|
| `clienteId` | UUID | sim | existe e está ativo |
| `placa` | string | sim | formato Mercosul `AAA9A99` ou antigo `AAA9999`; normalizada e única |
| `marca` | string | sim | `@NotBlank` |
| `modelo` | string | sim | `@NotBlank` |
| `ano` | int | sim | `@Positive` |

```json
{
  "clienteId": "c3d87d1a-a8e2-4d6e-a71c-1f5a9f8c0b14",
  "placa": "ABC1D23",
  "marca": "Fiat",
  "modelo": "Palio",
  "ano": 2015
}
```

**Response `201 Created`**

```json
{
  "id": "a1b2c3d4-e5f6-4789-abcd-1234567890ab",
  "clienteId": "c3d87d1a-a8e2-4d6e-a71c-1f5a9f8c0b14",
  "placa": "ABC1D23",
  "marca": "Fiat",
  "modelo": "Palio",
  "ano": 2015,
  "ativo": true
}
```

**Erros**: `400 VALIDATION_ERROR`, `400 PLATE_INVALID`, `404 CUSTOMER_NOT_FOUND`, `409 VEHICLE_PLATE_DUPLICATED`.

### `GET /api/v1/veiculos/{id}`

`200 OK` com `VeiculoResponse`. `404 VEHICLE_NOT_FOUND`.

### `GET /api/v1/veiculos?clienteId={uuid}&page={n}&size={n}`

Lista veículos. Se `clienteId` é informado, filtra por cliente; senão, lista paginado.

### `PUT /api/v1/veiculos/{id}`

Atualiza marca/modelo/ano. **Request body**:

```json
{ "marca": "Fiat", "modelo": "Palio", "ano": 2016 }
```

`200 OK`, `404 VEHICLE_NOT_FOUND`.

### `DELETE /api/v1/veiculos/{id}`

Desativa o veículo. `204 No Content`, `404 VEHICLE_NOT_FOUND`.

---

## 5. Serviços

Base: `/api/v1/servicos` — todas exigem `ROLE_FUNCIONARIO_DA_OFICINA`.

### `POST /api/v1/servicos`

Cria um serviço no catálogo.

**Request body**

| Campo | Tipo | Obrig. | Validação |
|---|---|---|---|
| `nome` | string | sim | `@NotBlank` |
| `descricao` | string | não | — |
| `precoBase` | decimal(12,2) | sim | `@PositiveOrZero` |
| `tempoEstimadoMinutos` | int | sim | `@Positive` |

```json
{
  "nome": "Troca de óleo",
  "descricao": "Inclui filtro",
  "precoBase": 150.00,
  "tempoEstimadoMinutos": 45
}
```

**Response `201 Created`**

```json
{
  "id": "0f2c4e91-...",
  "nome": "Troca de óleo",
  "descricao": "Inclui filtro",
  "precoBase": 150.00,
  "tempoEstimadoMinutos": 45,
  "ativo": true
}
```

### `GET /api/v1/servicos/{id}` · `GET /api/v1/servicos` · `PUT /api/v1/servicos/{id}` · `DELETE /api/v1/servicos/{id}`

Mesmo padrão dos endpoints de cliente/veículo. `DELETE` desativa (soft delete).

**Erros**: `400 VALIDATION_ERROR`, `404 SERVICE_NOT_FOUND`.

---

## 6. Peças

Base: `/api/v1/pecas` — todas exigem `ROLE_FUNCIONARIO_DA_OFICINA`.

### `POST /api/v1/pecas`

Cria peça no catálogo com saldo inicial de estoque.

**Request body**

| Campo | Tipo | Obrig. | Validação |
|---|---|---|---|
| `sku` | string | sim | único |
| `nome` | string | sim | `@NotBlank` |
| `precoVenda` | decimal(12,2) | sim | `@PositiveOrZero` |
| `estoque` | int | sim | `@PositiveOrZero` |

```json
{ "sku": "OLEO-5W30", "nome": "Óleo 5W30 1L", "precoVenda": 45.90, "estoque": 50 }
```

**Response `201 Created`**

```json
{
  "id": "2e4a...",
  "sku": "OLEO-5W30",
  "nome": "Óleo 5W30 1L",
  "precoVenda": 45.90,
  "estoque": 50,
  "ativo": true
}
```

**Erros**: `400 VALIDATION_ERROR`, `409 PART_SKU_DUPLICATED`.

### `GET /api/v1/pecas/{id}` · `GET /api/v1/pecas?page=&size=`

Busca individual / listagem paginada. `404 PART_NOT_FOUND`.

### `PUT /api/v1/pecas/{id}`

Atualiza **apenas** `nome` e `precoVenda`. Não altera estoque.

```json
{ "nome": "Óleo 5W30 1L (premium)", "precoVenda": 49.90 }
```

### `POST /api/v1/pecas/{id}/estoque`

Repõe saldo de estoque (soma `quantidade` ao `estoque` atual).

**Request body**

| Campo | Tipo | Obrig. | Validação |
|---|---|---|---|
| `quantidade` | int | sim | `@Positive` |

```json
{ "quantidade": 20 }
```

**Response `200 OK`**: `PecaResponse` com novo saldo. `404 PART_NOT_FOUND`.

### `DELETE /api/v1/pecas/{id}`

Desativa a peça. `204 No Content`, `404 PART_NOT_FOUND`.

> **Observação**: consumo de estoque (na aprovação da OS) NÃO é feito por este endpoint — é automático ao cliente aprovar o orçamento. Ver seção [Ordens de Serviço (público)](#8-ordens-de-serviço-público--cliente).

---

## 7. Ordens de Serviço (administrativo)

Base: `/api/v1/ordens-servico`. Papéis:

- **`FUNCIONARIO_DA_OFICINA`**: abrir OS, listar, buscar, registrar entrega.
- **`TECNICO_DA_OFICINA`**: listar, buscar, iniciar diagnóstico, adicionar/remover itens, enviar para aprovação, finalizar.

> **OrdemServicoResponse** (shape comum a todos os endpoints de OS):

```json
{
  "id": "e1d2...",
  "numero": "OS-2026-000001",
  "clienteId": "c3d8...",
  "veiculoId": "a1b2...",
  "status": "RECEBIDA",
  "valorOrcamento": 0.00,
  "aprovado": null,
  "decididoEm": null,
  "motivoRejeicao": null,
  "recebidaEm": "2026-04-20T14:23:11Z",
  "diagnosticadaEm": null,
  "aguardandoAprovacaoEm": null,
  "emExecucaoEm": null,
  "finalizadaEm": null,
  "entregueEm": null,
  "canceladaEm": null,
  "itens": [
    {
      "id": "9ad1...",
      "tipo": "SERVICO",
      "referenciaId": "0f2c...",
      "descricao": "Troca de óleo",
      "quantidade": 1,
      "precoUnitario": 150.00,
      "subtotal": 150.00
    }
  ]
}
```

### `POST /api/v1/ordens-servico`

Abre nova OS. **Auth**: `FUNCIONARIO_DA_OFICINA`.

**Request body**

| Campo | Tipo | Obrig. |
|---|---|---|
| `clienteId` | UUID | sim |
| `veiculoId` | UUID | sim — deve pertencer ao `clienteId` |

```json
{ "clienteId": "c3d8...", "veiculoId": "a1b2..." }
```

**Response `201 Created`**: `OrdemServicoResponse` com `status=RECEBIDA` e `numero=OS-YYYY-NNNNNN` gerado atomicamente.

**Erros**:
- `400 VALIDATION_ERROR`, `404 CLIENTE_NOT_FOUND`, `404 VEICULO_NOT_FOUND`
- `422 CLIENTE_INATIVO`, `422 VEICULO_INATIVO`
- `422 VEICULO_NAO_PERTENCE_AO_CLIENTE`

### `GET /api/v1/ordens-servico/{id}`

Busca detalhes da OS. **Auth**: ambos os papéis.

`200 OK`: `OrdemServicoResponse`. `404 ORDEM_SERVICO_NOT_FOUND`.

### `GET /api/v1/ordens-servico?page={n}&size={n}`

Lista paginada. **Auth**: ambos os papéis.

### `POST /api/v1/ordens-servico/{id}/diagnostico`

Técnico inicia diagnóstico. **Auth**: `TECNICO_DA_OFICINA`.

- Requer status atual = `RECEBIDA`. Transiciona para `EM_DIAGNOSTICO` (grava `diagnosticadaEm`).
- `200 OK`: `OrdemServicoResponse`. `422 OS_INVALID_STATUS_TRANSITION` se status ≠ `RECEBIDA`.

### `POST /api/v1/ordens-servico/{id}/servicos`

Técnico adiciona um serviço ao orçamento.

- **Auth**: `TECNICO_DA_OFICINA`.
- **Permitido em**: `RECEBIDA` ou `EM_DIAGNOSTICO`.

**Request body**

```json
{ "referenciaId": "0f2c... (id do serviço no catálogo)", "quantidade": 1 }
```

| Campo | Tipo | Obrig. | Validação |
|---|---|---|---|
| `referenciaId` | UUID | sim | id de um `servico` existente e ativo |
| `quantidade` | int | sim | `@Positive` |

**Response `200 OK`**: `OrdemServicoResponse` com o novo item e `valorOrcamento` recalculado. Preço unitário é **congelado** no momento da inclusão.

**Erros**: `404 SERVICE_NOT_FOUND`, `422 SERVICO_INATIVO`, `422 OS_INVALID_STATUS_TRANSITION`.

### `POST /api/v1/ordens-servico/{id}/pecas`

Técnico adiciona uma peça ao orçamento. Mesmo shape do endpoint acima, porém `referenciaId` aponta para uma peça.

**Erros**: `404 PECA_NOT_FOUND`, `422 PECA_INATIVA`, `422 OS_INVALID_STATUS_TRANSITION`.

> **Importante**: adicionar a peça ao orçamento **não** consome estoque. Só quando o cliente aprova.

### `DELETE /api/v1/ordens-servico/{id}/itens/{itemId}`

Remove um item do orçamento. **Auth**: `TECNICO_DA_OFICINA`.

- **Permitido em**: `RECEBIDA` ou `EM_DIAGNOSTICO`.
- `200 OK`: `OrdemServicoResponse` recalculado. `404 ORDEM_SERVICO_NOT_FOUND`, `404` se item não pertence à OS, `422 OS_INVALID_STATUS_TRANSITION`.

### `POST /api/v1/ordens-servico/{id}/enviar-aprovacao`

Técnico envia o orçamento para o cliente. **Auth**: `TECNICO_DA_OFICINA`.

- Requer status = `EM_DIAGNOSTICO` e pelo menos 1 item.
- Transiciona para `AGUARDANDO_APROVACAO` (grava `aguardandoAprovacaoEm`).
- **Erros**: `422 OS_INVALID_STATUS_TRANSITION`, `422 WO_EMPTY_BUDGET`.

### `POST /api/v1/ordens-servico/{id}/finalizar`

Técnico finaliza a execução. **Auth**: `TECNICO_DA_OFICINA`.

- Requer status = `EM_EXECUCAO`. Transiciona para `FINALIZADA` (grava `finalizadaEm`).

### `POST /api/v1/ordens-servico/{id}/entregar`

Funcionário registra entrega ao cliente. **Auth**: `FUNCIONARIO_DA_OFICINA`.

- Requer status = `FINALIZADA`. Transiciona para `ENTREGUE` (terminal; grava `entregueEm`).

---

## 8. Ordens de Serviço (público / cliente)

Base: `/api/v1/public/ordens-servico`. **Todas as rotas são públicas** — autorização feita pela combinação **número da OS + documento do cliente** (CPF/CNPJ que consta no cadastro).

### `GET /api/v1/public/ordens-servico/{numero}?documento={cpfOuCnpj}`

Consulta status e orçamento da OS.

- **Path param**: `numero` no formato `OS-YYYY-NNNNNN` (ex.: `OS-2026-000001`).
- **Query param**: `documento` (CPF ou CNPJ; aceita máscara).

**Exemplo**:

```
GET /api/v1/public/ordens-servico/OS-2026-000001?documento=52998224725
```

**Response `200 OK`**: `OrdemServicoResponse` completa (mesmo shape do admin).

**Erros**:
- `400 DOCUMENT_INVALID` — documento com DV inválido
- `400 OS_AUTH_REQUIRED` — query `documento` ausente
- `404 ORDEM_SERVICO_NOT_FOUND` — número não existe
- `404 CLIENTE_NOT_FOUND` — inconsistência
- `403-equivalente 400 OS_AUTH_INVALID` — documento não bate com o cliente da OS

### `POST /api/v1/public/ordens-servico/{numero}/aprovar`

Cliente aprova o orçamento.

**Request body**

```json
{ "documento": "52998224725" }
```

| Campo | Tipo | Obrig. |
|---|---|---|
| `documento` | string | sim |

**Response `200 OK`**: `OrdemServicoResponse` com `status=EM_EXECUCAO`, `aprovado=true`, `decididoEm` preenchido, `emExecucaoEm` preenchido.

**Efeito colateral transacional**: para cada item com `tipo=PECA`, o saldo de estoque é decrementado. Se **qualquer** peça tiver saldo insuficiente, a transação é abortada e nenhuma alteração é persistida.

**Erros**:
- `400 DOCUMENT_INVALID`, `400 OS_AUTH_INVALID`
- `404 ORDEM_SERVICO_NOT_FOUND`
- `422 OS_INVALID_STATUS_TRANSITION` — OS não está em `AGUARDANDO_APROVACAO`
- `422 PART_OUT_OF_STOCK` — uma ou mais peças sem estoque

### `POST /api/v1/public/ordens-servico/{numero}/rejeitar`

Cliente rejeita o orçamento. **Único caminho de cancelamento da OS**.

**Request body**

```json
{ "documento": "52998224725", "motivo": "Prefiro outro mecânico" }
```

| Campo | Tipo | Obrig. |
|---|---|---|
| `documento` | string | sim |
| `motivo` | string | não |

**Response `200 OK`**: `OrdemServicoResponse` com `status=CANCELADA`, `aprovado=false`, `decididoEm`, `canceladaEm`, `motivoRejeicao` preenchidos.

**Erros**: `400 DOCUMENT_INVALID`, `400 OS_AUTH_INVALID`, `404 ORDEM_SERVICO_NOT_FOUND`, `422 OS_INVALID_STATUS_TRANSITION` (fora de `AGUARDANDO_APROVACAO`).

---

## 9. Relatórios

Base: `/api/v1/relatorios`. **Auth**: `FUNCIONARIO_DA_OFICINA` ou `TECNICO_DA_OFICINA`.

### `GET /api/v1/relatorios/tempo-medio`

Tempo médio (em minutos) de execução por serviço, calculado sobre OS `FINALIZADA` ou `ENTREGUE`.

Fórmula: `média(finalizadaEm - emExecucaoEm)` agrupado pelo `servicoId` catalogado.

**Response `200 OK`**

```json
[
  {
    "servicoId": "0f2c4e91-...",
    "nomeServico": "Troca de óleo",
    "totalOrdensFinalizadas": 23,
    "tempoMedioMinutos": 47.8
  },
  {
    "servicoId": "a3d1e4b2-...",
    "nomeServico": "Alinhamento",
    "totalOrdensFinalizadas": 15,
    "tempoMedioMinutos": 62.4
  }
]
```

Lista vazia se não há OS finalizadas.

---

## Apêndice A — Códigos de erro

### Autenticação / Autorização
- `AUTH_INVALID_CREDENTIALS` (401) — email ou senha inválidos.
- `AUTH_FORBIDDEN` (403) — usuário autenticado, mas papel sem permissão para a rota.

### Validação
- `VALIDATION_ERROR` (400) — erro em `@NotBlank`, `@Email`, `@Positive` etc. Veja `details[]`.

### Shared VOs
- `DOCUMENT_INVALID` (400) — CPF/CNPJ ausente, com tamanho errado ou DV inválido.
- `PLATE_INVALID` (400) — placa fora do formato Mercosul ou antigo.
- `MONEY_INVALID`, `MONEY_INVALID_QUANTITY`, `MONEY_CURRENCY_MISMATCH` (400) — erros do VO `Money`.
- `STOCK_INVALID` (400) — quantidade negativa de estoque.

### Identity
- `USER_NOT_FOUND` (404), `USER_EMAIL_DUPLICATED` (409).

### Clientes
- `CUSTOMER_NOT_FOUND` / `CLIENTE_NOT_FOUND` (404).
- `CUSTOMER_DOCUMENT_DUPLICATED` (409).
- `CLIENTE_INATIVO` (422).

### Veículos
- `VEHICLE_NOT_FOUND` / `VEICULO_NOT_FOUND` (404).
- `VEHICLE_PLATE_DUPLICATED` (409).
- `VEICULO_INATIVO` (422).
- `VEICULO_NAO_PERTENCE_AO_CLIENTE` (422).

### Serviços
- `SERVICE_NOT_FOUND` / `SERVICO_NOT_FOUND` (404).
- `SERVICO_INATIVO` (422).

### Peças
- `PART_NOT_FOUND` / `PECA_NOT_FOUND` (404).
- `PART_SKU_DUPLICATED` (409).
- `PART_OUT_OF_STOCK` (422).
- `PECA_INATIVA` (422).

### Ordens de Serviço
- `ORDEM_SERVICO_NOT_FOUND` / `WORK_ORDER_NOT_FOUND` (404).
- `OS_INVALID_STATUS_TRANSITION` / `WO_INVALID_STATUS_TRANSITION` (422) — ex.: tentativa de finalizar quando ainda está `EM_DIAGNOSTICO`.
- `WO_EMPTY_BUDGET` (422) — tentativa de enviar para aprovação sem itens.
- `OS_ITEM_INVALID` (400) — item inválido ao adicionar.
- `OS_AUTH_REQUIRED` (400) — documento ausente no endpoint público.
- `OS_AUTH_INVALID` (400) — documento não corresponde ao cliente da OS.
- `BUDGET_NOT_APPROVED` (422) — ação requer orçamento previamente aprovado.

### Genéricos
- `INTERNAL_ERROR` (500) — falha inesperada.

---

## Apêndice B — Máquina de estados da OS

```
                 ┌───────────┐
                 │ RECEBIDA  │  ← POST /ordens-servico
                 └────┬──────┘
                      │ POST /{id}/diagnostico  (TÉCNICO)
                      ▼
              ┌──────────────────┐
              │ EM_DIAGNOSTICO   │  — adiciona serviços / peças (itens = orçamento)
              └────┬─────────────┘
                   │ POST /{id}/enviar-aprovacao  (TÉCNICO)
                   ▼
          ┌────────────────────────┐
          │ AGUARDANDO_APROVACAO   │
          └────┬────────────┬──────┘
   aprovar    │            │  rejeitar
 (público)    ▼            ▼  (público)
   ┌────────────────┐ ┌────────────┐
   │ EM_EXECUCAO    │ │ CANCELADA  │ (terminal)
   │ (estoque -)    │ └────────────┘
   └────┬───────────┘
        │ POST /{id}/finalizar  (TÉCNICO)
        ▼
   ┌─────────────┐
   │ FINALIZADA  │
   └────┬────────┘
        │ POST /{id}/entregar  (FUNCIONÁRIO)
        ▼
   ┌─────────────┐
   │ ENTREGUE    │ (terminal)
   └─────────────┘
```

**Regras invariantes**:

- Itens do orçamento só podem ser adicionados/removidos em `RECEBIDA` ou `EM_DIAGNOSTICO`.
- O envio para aprovação exige ao menos 1 item (`WO_EMPTY_BUDGET`).
- Consumo de estoque das peças ocorre **no momento da aprovação pelo cliente**, de forma transacional — se qualquer peça não tiver saldo, a aprovação é abortada e nada é persistido.
- O cancelamento só acontece por **rejeição do cliente**. Não há rota admin para cancelar.
- Preço dos itens é **congelado** no momento da inclusão — alterações posteriores no catálogo (ex.: preço de peça) não afetam OS já existentes.
