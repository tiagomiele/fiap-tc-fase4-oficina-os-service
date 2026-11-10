# RFC 0003 — Autenticação por CPF e JWT

- **Status:** aprovada e implementada

## Contexto

Clientes precisam consultar recursos protegidos sem utilizar as credenciais administrativas da equipe da oficina.

## Decisão

Expor `POST /auth/cpf` no API Gateway. Uma Lambda normaliza e valida o CPF, consulta cliente ativo no PostgreSQL e emite JWT RSA de curta duração. Uma Lambda Authorizer valida o token antes das rotas protegidas.

## Segurança

- CPF mascarado em logs.
- Falhas retornam resposta genérica.
- Assinatura assimétrica separa emissão e validação.
- Token contém somente claims necessárias.
- Backend realiza validação adicional e controle de propriedade.

## Consequências

A rotação das chaves precisa ser controlada e o tempo de cold start deve ser monitorado.
