# Visão de negócio e engenharia: Fase 4

Voltar para a [documentação central](../README.md).

## 1. Contexto de negócio

A Oficina apoia a operação de uma oficina mecânica. Ela reúne num só sistema informações que costumam ficar espalhadas entre o atendimento, a equipe técnica, o estoque, os fornecedores e o financeiro.

O sistema acompanha o veículo da recepção até a entrega e registra o cliente, o diagnóstico, o orçamento, as peças, os serviços, o pagamento e cada mudança de status da OS. A Fase 4 mantém esse domínio e divide o backend em microsserviços, para que cada área do negócio evolua e seja publicada de forma independente.

### Usuários e interesses

| Perfil | Necessidade atendida |
|---|---|
| Cliente | Entrar com o CPF, consultar a OS, aprovar ou rejeitar o orçamento e pagar. |
| Atendente | Cadastrar clientes e veículos e abrir a OS. |
| Técnico | Pegar a OS na fila, diagnosticar, incluir serviços e peças, concluir o reparo e registrar a entrega. |
| Gestor | Consultar relatórios, tempos médios, volume de OS e a saúde da operação. |
| Operação de tecnologia | Subir, escalar, observar e destruir a plataforma de forma reproduzível. |

## 2. Capacidades funcionais por serviço

| Área de negócio | Funcionalidades principais | Serviço |
|---|---|---|
| Atendimento | Clientes, veículos, abertura e acompanhamento da OS e consulta pública do status | OS |
| Catálogo e estoque | Serviços, peças, saldo, entrada por nota fiscal de fornecedor e baixa na aprovação | OS |
| Financeiro e gestão | Contas a receber e a pagar, relatórios por status e tempo médio | OS |
| Execução técnica | Fila do técnico, diagnóstico e reparo | Diagnóstico e Reparo |
| Orçamento | Orçamento gerado pela Saga; o cliente aprova, rejeita para refazer ou cancela | Orçamento e Pagamento |
| Pagamento | Mercado Pago Checkout Pro (sandbox) ou fake, com webhook assinado | Orçamento e Pagamento |
| Acesso do cliente | Login por CPF, JWT de curta duração e notificações | Auth |

## 3. Jornada da OS entre os serviços

| # | Etapa | Mensagem | Serviço que atua | Status da OS depois |
|---|---|---|---|---|
| 1 | Abertura | `INICIAR_DIAGNOSTICO` | Diagnóstico e Reparo | `RECEBIDA` |
| 2 | Diagnóstico | `DIAGNOSTICO_INICIADO` / `DIAGNOSTICO_CONCLUIDO` | Diagnóstico e Reparo | `EM_DIAGNOSTICO` / `AGUARDANDO_APROVACAO` |
| 3 | Orçamento | `GERAR_ORCAMENTO` | Orçamento e Pagamento | `AGUARDANDO_APROVACAO` |
| 4 | Aprovação | `ORCAMENTO_APROVADO` | OS (baixa do estoque) | `EM_EXECUCAO` |
| 5 | Reparo | `INICIAR_REPARO` / `REPARO_CONCLUIDO` | Diagnóstico e Reparo | `AGUARDANDO_PAGAMENTO` |
| 6 | Pagamento | `SOLICITAR_PAGAMENTO` / `PAGAMENTO_CONFIRMADO` | Orçamento e Pagamento | `PAGA` |
| 7 | Entrega | — | OS | `ENTREGUE` |

O OS Service guarda o status da OS, que é o estado da Saga. Comandos, eventos e compensações estão em [Saga](saga/README.md).

## 4. Evolução da Fase 3 para a Fase 4

| Necessidade de evolução | Resposta da Fase 4 |
|---|---|
| Backend único concentrando todas as áreas | 3 microsserviços, cada um com código, imagem, namespace e pipeline próprios |
| Uma mudança exigia publicar tudo | Deploy independente por serviço |
| Um banco para todo o domínio | Banco por serviço: 2 PostgreSQL e 1 DynamoDB |
| Consistência entre serviços sem transação distribuída | Saga orquestrada pelo SQS, com DLQ, idempotência e compensações |
| Pagamento real | Mercado Pago Checkout Pro (sandbox) com webhook assinado |
| Auth acessava o banco do backend | Auth consulta o OS por REST (`POST /interno/clientes/autenticacao`) |
| Validar o fluxo entre serviços | BDD com Cucumber e LocalStack, E2E com Newman |
| Rastrear uma OS entre serviços | `correlationId` nos logs e nas mensagens, com APM e dashboards no New Relic |

## 5. Modelo arquitetural por aplicação

### Microsserviços (OS, Orçamento e Pagamento, Diagnóstico e Reparo)

Os 3 serviços seguem **Clean Architecture em 4 anéis**, no estilo de portas e adaptadores:

```text
infrastructure → adapter → usecase → domain
```

- **domain:** entidades, regras e exceções em Java puro;
- **usecase:** casos de uso e portas (repositórios, mensageria, integrações);
- **adapter:** controllers, DTOs, persistência, SQS, segurança e integrações externas;
- **infrastructure:** configuração do Spring e composição da aplicação.

As dependências apontam para o domínio, e testes ArchUnit verificam esses limites em cada serviço.

### Auth Serverless

Camadas leves (`domain`, `application`, `handler`, `infrastructure`, `notification` e `observability`), adequadas a Lambdas pequenas e com uma responsabilidade cada.

### Database Infra e Kubernetes Infra

Infraestrutura como código com Terraform no HCP Terraform. O Database tem uma pasta, um workspace e um workflow para cada serviço. O schema dos bancos relacionais fica nas migrations Flyway de cada serviço.

## 6. Práticas de desenvolvimento

| Prática | Aplicação na solução |
|---|---|
| DDD | Cada serviço cobre um contexto do domínio: atendimento, execução técnica e orçamento e pagamento. |
| Clean Architecture | Isola as regras de negócio dos frameworks nos 3 serviços. |
| SOLID e Clean Code | Portas e adaptadores, nomes do negócio, formatação automática (Spotless) e revisão por Pull Request. |
| Testes automatizados | JUnit 5, RestAssured, Testcontainers (LocalStack), ArchUnit, BDD Cucumber e cobertura mínima de 80% (JaCoCo). |
| Qualidade e segurança | SonarCloud, SBOM CycloneDX, Dependency-Track, Trivy, Checkov, TFLint, ShellCheck, actionlint e Gitleaks. |
| Infraestrutura reproduzível | Terraform, HCP Terraform e scripts para configurar, subir e destruir os ambientes. |
| Observabilidade | Logs JSON, `correlationId`, traces, APM, dashboards, alertas e Synthetic no New Relic. |
| Entrega controlada | `feature → homolog → main`, com PR e checks obrigatórios e branches protegidas por rulesets. |

## 7. Limites dos 6 repositórios

| Projeto | Contribuição para o negócio | Responsabilidade técnica |
|---|---|---|
| OS Service | Conduz o atendimento e a jornada da OS. | API Spring Boot, orquestração da Saga, migrations e deploy no EKS. |
| Orçamento e Pagamento | Apresenta o orçamento e recebe o pagamento. | API Spring Boot, Mercado Pago, webhook, migrations e deploy no EKS. |
| Diagnóstico e Reparo | Organiza o trabalho do técnico. | API Spring Boot, DynamoDB e deploy no EKS. |
| Auth Serverless | Dá acesso seguro ao cliente. | API Gateway, Lambdas, JWT RS256, Authorizer e notificações. |
| Database Infra | Guarda os dados e as mensagens. | 2 RDS PostgreSQL, DynamoDB, 3 filas SQS com DLQ e telemetria. |
| Kubernetes Infra | Mantém os serviços disponíveis e observáveis. | VPC, EKS, add-ons e New Relic. |
