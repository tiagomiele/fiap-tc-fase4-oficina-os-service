package br.com.oficina.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatusOrdemServicoTest {

  @Test
  void statusAtivosSaoVisiveisNaListagem() {
    assertThat(StatusOrdemServico.RECEBIDA.visivelNaListagem()).isTrue();
    assertThat(StatusOrdemServico.EM_DIAGNOSTICO.visivelNaListagem()).isTrue();
    assertThat(StatusOrdemServico.AGUARDANDO_APROVACAO.visivelNaListagem()).isTrue();
    assertThat(StatusOrdemServico.EM_EXECUCAO.visivelNaListagem()).isTrue();
    assertThat(StatusOrdemServico.AGUARDANDO_PAGAMENTO.visivelNaListagem()).isTrue();
  }

  @Test
  void statusFinalizadosNaoSaoVisiveisNaListagem() {
    assertThat(StatusOrdemServico.PAGA.visivelNaListagem()).isFalse();
    assertThat(StatusOrdemServico.ENTREGUE.visivelNaListagem()).isFalse();
    assertThat(StatusOrdemServico.CANCELADA.visivelNaListagem()).isFalse();
  }

  @Test
  void prioridadeDeListagemSegueOrdemDoEdital() {
    assertThat(StatusOrdemServico.EM_EXECUCAO.getPrioridadeListagem())
        .isLessThan(StatusOrdemServico.AGUARDANDO_APROVACAO.getPrioridadeListagem());
    assertThat(StatusOrdemServico.AGUARDANDO_APROVACAO.getPrioridadeListagem())
        .isLessThan(StatusOrdemServico.EM_DIAGNOSTICO.getPrioridadeListagem());
    assertThat(StatusOrdemServico.EM_DIAGNOSTICO.getPrioridadeListagem())
        .isLessThan(StatusOrdemServico.RECEBIDA.getPrioridadeListagem());
  }
}
