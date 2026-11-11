# BDD da Saga

Cenários Cucumber (em português) que sobem os 3 serviços da Fase 4 e testam a Saga de ponta a ponta,
só pelas filas SQS:

| Cenário | O que confere |
|---|---|
| Fluxo feliz | OS aberta → diagnóstico → orçamento aprovado → baixa de estoque → reparo → pagamento aprovado → entrega, com o mesmo `correlationId` em todas as etapas da Saga |
| Estoque insuficiente | 2 OS disputam a mesma peça; a segunda aprovação cancela a OS, o orçamento e a execução, sem baixar estoque |
| Orçamento rejeitado (cancelar) | OS cancelada, orçamento rejeitado e execução cancelada |
| Orçamento rejeitado (refazer) | OS volta para diagnóstico e a execução volta para a fila |
| Pagamento recusado | OS continua aguardando pagamento; nova tentativa e pagamento aprovado levam a OS para paga |

## Como rodar

Pré-requisitos: Docker, Java 21 e `openssl`. Os repositórios `fiap-tc-fase4-oficina-diagnostico-reparo-service` e
`fiap-tc-fase4-oficina-orcamento-pagamento-service` devem estar clonados ao lado deste.

```bash
cd bdd
./run-bdd.sh
```

O script gera um par de chaves RSA de teste (o "auth-serverless" do BDD), sobe o
`docker-compose.yml` (3 serviços, 2 Postgres e LocalStack com as filas, DLQs e a tabela DynamoDB),
espera os 3 health checks e roda o Maven. O relatório fica em `bdd/target/cucumber-report.html`.
Use `BDD_KEEP_UP=true ./run-bdd.sh` para manter os containers depois do teste.

No GitHub, o workflow `BDD Saga` roda os mesmos cenários em todo PR e push para `homolog` e `main`.
