package br.com.oficina.usecase.saga;

import br.com.oficina.domain.enums.StatusOrdemServico;

/**
 * Contrato das mensagens da Saga. Comandos saem do OS Service para os outros serviços; eventos
 * chegam na fila {@code os-eventos} e só são aplicados quando a OS está no status esperado.
 */
public enum TipoMensagemSaga {
  INICIAR_DIAGNOSTICO(DestinoMensagem.DIAGNOSTICO_REPARO, null),
  INICIAR_REPARO(DestinoMensagem.DIAGNOSTICO_REPARO, null),
  CANCELAR_EXECUCAO(DestinoMensagem.DIAGNOSTICO_REPARO, null),
  GERAR_ORCAMENTO(DestinoMensagem.ORCAMENTO_PAGAMENTO, null),
  CANCELAR_ORCAMENTO(DestinoMensagem.ORCAMENTO_PAGAMENTO, null),
  SOLICITAR_PAGAMENTO(DestinoMensagem.ORCAMENTO_PAGAMENTO, null),

  DIAGNOSTICO_INICIADO(DestinoMensagem.OS_SERVICE, StatusOrdemServico.RECEBIDA),
  DIAGNOSTICO_CONCLUIDO(DestinoMensagem.OS_SERVICE, StatusOrdemServico.EM_DIAGNOSTICO),
  ORCAMENTO_APROVADO(DestinoMensagem.OS_SERVICE, StatusOrdemServico.AGUARDANDO_APROVACAO),
  ORCAMENTO_REJEITADO(DestinoMensagem.OS_SERVICE, StatusOrdemServico.AGUARDANDO_APROVACAO),
  REPARO_CONCLUIDO(DestinoMensagem.OS_SERVICE, StatusOrdemServico.EM_EXECUCAO),
  PAGAMENTO_CONFIRMADO(DestinoMensagem.OS_SERVICE, StatusOrdemServico.AGUARDANDO_PAGAMENTO),
  PAGAMENTO_RECUSADO(DestinoMensagem.OS_SERVICE, StatusOrdemServico.AGUARDANDO_PAGAMENTO);

  private final DestinoMensagem destino;
  private final StatusOrdemServico statusEsperado;

  TipoMensagemSaga(DestinoMensagem destino, StatusOrdemServico statusEsperado) {
    this.destino = destino;
    this.statusEsperado = statusEsperado;
  }

  public DestinoMensagem destino() {
    return destino;
  }

  public StatusOrdemServico statusEsperado() {
    return statusEsperado;
  }

  public boolean isEvento() {
    return destino == DestinoMensagem.OS_SERVICE;
  }
}
