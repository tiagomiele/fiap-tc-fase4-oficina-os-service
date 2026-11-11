# language: pt
Funcionalidade: Saga da ordem de serviço entre OS, Diagnóstico e Reparo e Orçamento e Pagamento
  Os 3 serviços conversam só pelas filas SQS. O OS Service orquestra a Saga
  pelo status da OS e cada serviço grava no próprio banco.

  Contexto:
    Dado uma peça com estoque 3

  Cenário: Fluxo feliz, da abertura até a entrega
    Dado uma OS "A" em diagnóstico com 1 unidade da peça
    Quando o técnico conclui o diagnóstico da OS "A"
    Então a OS "A" fica "AGUARDANDO_APROVACAO"
    E o orçamento da OS "A" fica "AGUARDANDO_APROVACAO"
    Quando o cliente aprova o orçamento da OS "A"
    Então a OS "A" fica "EM_EXECUCAO"
    E a execução da OS "A" fica "NA_FILA_REPARO"
    E o saldo da peça é 2
    Quando o técnico executa o reparo da OS "A"
    Então a OS "A" fica "AGUARDANDO_PAGAMENTO"
    E o pagamento da OS "A" fica "PENDENTE"
    Quando o pagamento da OS "A" é aprovado
    Então a OS "A" fica "PAGA"
    E o orçamento da OS "A" fica "PAGO"
    E todas as etapas da Saga no histórico da OS "A" têm o correlationId da abertura
    Quando a OS "A" é entregue
    Então a OS "A" fica "ENTREGUE"

  Cenário: Compensação por estoque insuficiente na aprovação
    Dado uma OS "A" em diagnóstico com 2 unidades da peça
    E uma OS "B" em diagnóstico com 2 unidades da peça
    E o técnico conclui o diagnóstico da OS "A"
    E o técnico conclui o diagnóstico da OS "B"
    E o orçamento da OS "A" fica "AGUARDANDO_APROVACAO"
    E o orçamento da OS "B" fica "AGUARDANDO_APROVACAO"
    Quando o cliente aprova o orçamento da OS "A"
    E a OS "A" fica "EM_EXECUCAO"
    E o cliente aprova o orçamento da OS "B"
    Então a OS "B" fica "CANCELADA"
    E o orçamento da OS "B" fica "CANCELADO"
    E a execução da OS "B" fica "CANCELADA"
    E o saldo da peça é 1

  Cenário: Compensação por orçamento rejeitado com cancelamento
    Dado uma OS "A" em diagnóstico com 1 unidade da peça
    E o técnico conclui o diagnóstico da OS "A"
    E o orçamento da OS "A" fica "AGUARDANDO_APROVACAO"
    Quando o cliente rejeita o orçamento da OS "A" com a ação "CANCELAR"
    Então a OS "A" fica "CANCELADA"
    E o orçamento da OS "A" fica "REJEITADO"
    E a execução da OS "A" fica "CANCELADA"
    E o saldo da peça é 3

  Cenário: Orçamento rejeitado para refazer volta para o diagnóstico
    Dado uma OS "A" em diagnóstico com 1 unidade da peça
    E o técnico conclui o diagnóstico da OS "A"
    E o orçamento da OS "A" fica "AGUARDANDO_APROVACAO"
    Quando o cliente rejeita o orçamento da OS "A" com a ação "REFAZER"
    Então a OS "A" fica "EM_DIAGNOSTICO"
    E a execução da OS "A" fica "NA_FILA_DIAGNOSTICO"

  Cenário: Compensação por pagamento recusado e nova tentativa
    Dado uma OS "A" em diagnóstico com 1 unidade da peça
    E o técnico conclui o diagnóstico da OS "A"
    E o orçamento da OS "A" fica "AGUARDANDO_APROVACAO"
    E o cliente aprova o orçamento da OS "A"
    E o técnico executa o reparo da OS "A"
    E o pagamento da OS "A" fica "PENDENTE"
    Quando o pagamento da OS "A" é recusado
    Então o pagamento da OS "A" fica "RECUSADO"
    E a OS "A" continua "AGUARDANDO_PAGAMENTO"
    Quando o cliente pede nova tentativa de pagamento da OS "A"
    Então o pagamento da OS "A" fica "PENDENTE"
    Quando o pagamento da OS "A" é aprovado
    Então a OS "A" fica "PAGA"
