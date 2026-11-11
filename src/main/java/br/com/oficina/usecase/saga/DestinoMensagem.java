package br.com.oficina.usecase.saga;

/** Serviço dono da fila SQS que recebe a mensagem. */
public enum DestinoMensagem {
  OS_SERVICE,
  DIAGNOSTICO_REPARO,
  ORCAMENTO_PAGAMENTO
}
