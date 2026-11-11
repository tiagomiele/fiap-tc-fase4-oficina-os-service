# Saga da OS — contrato das mensagens

O OS Service orquestra a Saga. O estado da Saga é o próprio status da OS (`StatusOrdemServico`) e o histórico em `historico_status_ordem_servico`; não há tabela de Saga nem Outbox.

## Filas

| Fila (`oficina-<env>-...`) | Quem consome | Quem publica | Variável no OS Service |
|---|---|---|---|
| `os-eventos` | OS Service | Diagnóstico e Reparo, Orçamento e Pagamento | `SQS_OS_EVENTOS_URL` |
| `diagnostico-reparo-comandos` | Diagnóstico e Reparo | OS Service | `SQS_DIAGNOSTICO_REPARO_COMANDOS_URL` |
| `orcamento-pagamento-comandos` | Orçamento e Pagamento | OS Service | `SQS_ORCAMENTO_PAGAMENTO_COMANDOS_URL` |

Cada fila tem DLQ (`<fila>-dlq`) com `maxReceiveCount = 5`. A mensagem só é apagada depois de processada; se o processamento falhar, ela volta para a fila e, depois de 5 tentativas, vai para a DLQ.

`MENSAGERIA_TIPO=sqs` liga o SQS. O padrão `memoria` só registra os comandos em log (uso local e testes). `AWS_SQS_ENDPOINT` aponta para o LocalStack; vazio usa a AWS com a LabRole do nó.

## Envelope (JSON)

```json
{
  "messageId": "6f1c0e8e-5c1b-4d43-9a43-2a9f3f0e2b11",
  "tipo": "ORCAMENTO_APROVADO",
  "correlationId": "c3d1f2a0-...",
  "numeroOs": "OS-102026-000001",
  "origem": "fiap-tc-fase4-oficina-orcamento-pagamento-service",
  "ocorridoEm": "2026-10-29T12:00:00Z",
  "payload": {}
}
```

- `messageId`: único por mensagem; é a chave de idempotência (tabela `mensagens_processadas`).
- `correlationId`: nasce no `X-Request-Id` da requisição que abriu a OS e deve ser repetido em todas as mensagens da mesma OS. Aparece nos logs (MDC `requestId` e `correlationId`) e no histórico de status.
- Os mesmos valores vão como atributos SQS `tipo` e `correlationId`.

## Comandos enviados pelo OS Service

| Tipo | Destino | Quando | Payload |
|---|---|---|---|
| `INICIAR_DIAGNOSTICO` | Diagnóstico e Reparo | OS aberta, ou orçamento rejeitado com refazer | `idCliente`, `placa`, `descricaoProblema` / `refazer`, `motivo` |
| `GERAR_ORCAMENTO` | Orçamento e Pagamento | `DIAGNOSTICO_CONCLUIDO` aplicado | `idCliente`, `valorTotal`, `itens[]` (`tipo`, `idServicoSku`, `descricao`, `quantidade`, `precoUnitario`) |
| `INICIAR_REPARO` | Diagnóstico e Reparo | orçamento aprovado e peças reservadas | — |
| `SOLICITAR_PAGAMENTO` | Orçamento e Pagamento | `REPARO_CONCLUIDO` aplicado | `idCliente`, `valor` |
| `CANCELAR_ORCAMENTO` | Orçamento e Pagamento | compensação: estoque insuficiente | `motivo` |
| `CANCELAR_EXECUCAO` | Diagnóstico e Reparo | compensações e cancelamento em diagnóstico | `motivo` |

## Eventos consumidos pelo OS Service

| Tipo | Publicado por | Status exigido | Efeito na OS | Próximo comando |
|---|---|---|---|---|
| `DIAGNOSTICO_INICIADO` | Diagnóstico e Reparo | `RECEBIDA` | `EM_DIAGNOSTICO` | — |
| `DIAGNOSTICO_CONCLUIDO` | Diagnóstico e Reparo | `EM_DIAGNOSTICO` | `AGUARDANDO_APROVACAO` | `GERAR_ORCAMENTO` |
| `ORCAMENTO_APROVADO` | Orçamento e Pagamento | `AGUARDANDO_APROVACAO` | reserva peças → `EM_EXECUCAO` | `INICIAR_REPARO` |
| `ORCAMENTO_REJEITADO` (`acao`=`REFAZER`) | Orçamento e Pagamento | `AGUARDANDO_APROVACAO` | `EM_DIAGNOSTICO`, novo orçamento | `INICIAR_DIAGNOSTICO` |
| `ORCAMENTO_REJEITADO` (`acao`=`CANCELAR`) | Orçamento e Pagamento | `AGUARDANDO_APROVACAO` | `CANCELADA` | `CANCELAR_EXECUCAO` |
| `REPARO_CONCLUIDO` | Diagnóstico e Reparo | `EM_EXECUCAO` | `AGUARDANDO_PAGAMENTO` | `SOLICITAR_PAGAMENTO` |
| `PAGAMENTO_CONFIRMADO` | Orçamento e Pagamento | `AGUARDANDO_PAGAMENTO` | `PAGA` + conta a receber | — |
| `PAGAMENTO_RECUSADO` | Orçamento e Pagamento | `AGUARDANDO_PAGAMENTO` | continua `AGUARDANDO_PAGAMENTO`, cliente notificado | — |

`PAGAMENTO_CONFIRMADO` usa `payload.comprovante` (ou `pagamentoId`). `ORCAMENTO_REJEITADO` e `PAGAMENTO_RECUSADO` aceitam `payload.motivo`.

## Compensações

1. **Estoque insuficiente:** no `ORCAMENTO_APROVADO`, se o saldo não cobrir as peças, a OS é cancelada e o OS envia `CANCELAR_ORCAMENTO` e `CANCELAR_EXECUCAO`.
2. **Orçamento rejeitado com cancelamento:** a OS é cancelada e o OS envia `CANCELAR_EXECUCAO`.
3. **Pagamento recusado:** a OS fica em `AGUARDANDO_PAGAMENTO` e o cliente é avisado para tentar de novo.

## Idempotência e ordem

- `messageId` repetido é ignorado (`mensagens_processadas`).
- Evento que chega com a OS em outro status (duplicado com outro `messageId`, atrasado ou fora de ordem) é registrado em log e ignorado.
- Erro no processamento desfaz a transação (inclusive o registro de idempotência) e a mensagem volta para a fila.
- Os comandos só são enviados ao SQS depois do commit da transação.

## Estoque

As peças continuam no OS Service. Ao adicionar uma peça, o OS só confere o saldo; a baixa acontece quando o orçamento é aprovado (reserva da Saga). Por isso rejeição e cancelamento antes da aprovação não precisam devolver peças.

## Rotas que saíram do OS Service

| Rota da Fase 3 | Novo dono |
|---|---|
| `POST /ordens-servico/{n}/enviar-para-aprovacao` | Diagnóstico e Reparo (concluir diagnóstico → `DIAGNOSTICO_CONCLUIDO`) |
| `POST /ordens-servico/{n}/concluir-reparo` | Diagnóstico e Reparo (`REPARO_CONCLUIDO`) |
| `POST /ordens-servico/{n}/aprovar` | Orçamento e Pagamento (`ORCAMENTO_APROVADO`) |
| `POST /ordens-servico/{n}/rejeitar-refazer` e `/rejeitar-cancelar` | Orçamento e Pagamento (`ORCAMENTO_REJEITADO`) |
| `POST /ordens-servico/{n}/confirmar-pagamento` | webhook do Mercado Pago no Orçamento e Pagamento (`PAGAMENTO_CONFIRMADO`) |
