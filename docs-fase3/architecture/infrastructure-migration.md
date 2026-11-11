# Migração da infraestrutura da Fase 2

## Resultado

A infraestrutura combinada da Fase 2 foi removida deste repositório. A Fase 3 mantém estados, pipelines e responsabilidades independentes:

| Repositório | Recursos |
|---|---|
| `fiap-tech-challenge-fase3-fiap-tc-fase4-oficina-kubernetes-infra` | VPC, subnets, rotas, NAT Gateway, EKS e node group |
| `fiap-tech-challenge-fase3-fiap-tc-fase4-oficina-database-infra` | DB subnet group, security group, parameter group e RDS PostgreSQL |
| `fiap-tech-challenge-fase3-oficina-backend` | imagem, Deployment, Service, HPA e configuração da aplicação |

O state combinado da Fase 2 não deve ser associado aos novos workspaces. Os ambientes da Fase 3 começam em states independentes.

## Ordem de execução

1. aplicar a infraestrutura Kubernetes após revisão do plan;
2. copiar `vpc_id`, `private_subnet_ids` e `eks_cluster_security_group_id`;
3. configurar esses valores no workspace do banco;
4. aplicar o RDS após revisão do plan;
5. copiar `jdbc_url` e `database_identifier` para o GitHub Environment da aplicação;
6. executar o deploy da aplicação.

## GitHub Environments da aplicação

Configure `homolog` e `production`.

Variáveis:

```text
AWS_REGION=us-west-2
EKS_CLUSTER_NAME=oficina-homolog ou oficina-production
APP_DB_URL=jdbc:postgresql://<endpoint>:5432/oficina
APP_DB_USER=oficina_admin
```

Secrets temporários ou sensíveis:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_SESSION_TOKEN
APP_DB_PASSWORD
APP_JWT_SECRET
APP_ADMIN_PASSWORD
```

As credenciais AWS devem ser renovadas sempre que a sessão do Learner Lab mudar. O pipeline não usa mais Postgres dentro do cluster, `secret.yaml` versionado ou `KUBECONFIG` estático.

## Segurança operacional

- nenhum Terraform permanece neste repositório;
- nenhum manifesto contém senha ou token padrão;
- a aplicação recebe banco e secrets pelo GitHub Environment;
- o deploy valida a sessão AWS antes de acessar o EKS;
- nenhum apply Terraform é executado pela pipeline da aplicação.
