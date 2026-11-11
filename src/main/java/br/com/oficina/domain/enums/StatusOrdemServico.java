package br.com.oficina.domain.enums;

public enum StatusOrdemServico {
  RECEBIDA(4),
  EM_DIAGNOSTICO(3),
  AGUARDANDO_APROVACAO(2),
  EM_EXECUCAO(1),
  AGUARDANDO_PAGAMENTO(5),
  PAGA(6),
  ENTREGUE(7),
  CANCELADA(8);

  private final int prioridadeListagem;

  StatusOrdemServico(int prioridadeListagem) {
    this.prioridadeListagem = prioridadeListagem;
  }

  public int getPrioridadeListagem() {
    return prioridadeListagem;
  }

  public boolean visivelNaListagem() {
    return this != PAGA && this != ENTREGUE && this != CANCELADA;
  }
}
