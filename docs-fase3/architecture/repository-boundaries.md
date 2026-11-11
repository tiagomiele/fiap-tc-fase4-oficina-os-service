# Limites dos repositórios

## 1. Aplicação principal

**Repositório:** `fiap-tech-challenge-fase3-oficina-backend`

Responsável por domínio, casos de uso, APIs, persistência, Flyway, instrumentação APM, Docker e artefatos de implantação da aplicação.

Não deve provisionar VPC, EKS, RDS, Lambda ou API Gateway.

## 2. Autenticação serverless

**Repositório:** `fiap-tech-challenge-fase3-fiap-tc-fase4-oficina-auth-serverless`

Responsável por validação de CPF, consulta do cliente, emissão de JWT, Lambda Authorizer, API Gateway e deploy serverless.

Não contém regras da Ordem de Serviço nem migrações do banco da aplicação.

## 3. Infraestrutura Kubernetes

**Repositório:** `fiap-tech-challenge-fase3-fiap-tc-fase4-oficina-kubernetes-infra`

Responsável por VPC, subnets, EKS, node groups, autoscaling, Load Balancer, namespaces e integração Kubernetes do New Relic.

Não constrói a imagem da aplicação e não administra o schema do banco.

## 4. Infraestrutura do banco

**Repositório:** `fiap-tech-challenge-fase3-fiap-tc-fase4-oficina-database-infra`

Responsável por RDS, subnet group, security groups, parâmetros, backups e outputs de conexão sem credenciais.

As migrações Flyway permanecem na aplicação, que é proprietária do schema funcional.

## Integrações

| Origem | Destino | Contrato |
|---|---|---|
| API Gateway | Lambda de login | HTTP/OpenAPI |
| API Gateway | Lambda Authorizer | Evento de autorização AWS |
| API Gateway | aplicação EKS | HTTP/OpenAPI |
| Lambda | RDS | schema de leitura de cliente |
| aplicação | RDS | JPA/Flyway |
| pipelines | HCP Terraform | workspace e variáveis |
| aplicação/Lambda/EKS | New Relic | APM, logs, métricas e eventos |

## Compartilhamento de informações

Outputs não sensíveis são transferidos explicitamente entre workspaces e GitHub Environments. Na Semana 2, os outputs de rede do repositório Kubernetes alimentam as variáveis do banco, e os outputs do RDS alimentam o deploy da aplicação. Credenciais e chaves são fornecidas por GitHub Environments e HCP Terraform; nunca por arquivos versionados.
