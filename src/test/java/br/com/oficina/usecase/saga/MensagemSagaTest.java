package br.com.oficina.usecase.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.enums.StatusOrdemServico;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MensagemSagaTest {

  @Test
  void comandoGeraIdentificadoresEPayloadImutavel() {
    MensagemSaga m =
        MensagemSaga.comando(
            TipoMensagemSaga.GERAR_ORCAMENTO, "OS-102026-000001", MensagemSaga.dados("a", 1));
    assertThat(m.messageId()).isNotBlank();
    assertThat(m.origem()).isEqualTo(MensagemSaga.ORIGEM_OS_SERVICE);
    assertThat(m.correlationId()).isNull();
    assertThat(m.texto("a")).isEqualTo("1");
    assertThat(m.texto("x")).isNull();
    assertThatThrownBy(() -> m.payload().put("b", 2))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(m.comCorrelationId("c").correlationId()).isEqualTo("c");
  }

  @Test
  void dadosIgnoraNulosEExigePares() {
    assertThat(MensagemSaga.dados("a", null, "b", 2)).isEqualTo(Map.of("b", 2));
    assertThatThrownBy(() -> MensagemSaga.dados("a")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void payloadNuloViraVazio() {
    MensagemSaga m =
        new MensagemSaga("id", TipoMensagemSaga.DIAGNOSTICO_INICIADO, null, "OS", "x", null, null);
    assertThat(m.payload()).isEmpty();
  }

  @Test
  void tiposSeparamComandosDeEventos() {
    assertThat(TipoMensagemSaga.INICIAR_DIAGNOSTICO.isEvento()).isFalse();
    assertThat(TipoMensagemSaga.INICIAR_DIAGNOSTICO.destino())
        .isEqualTo(DestinoMensagem.DIAGNOSTICO_REPARO);
    assertThat(TipoMensagemSaga.ORCAMENTO_APROVADO.isEvento()).isTrue();
    assertThat(TipoMensagemSaga.ORCAMENTO_APROVADO.statusEsperado())
        .isEqualTo(StatusOrdemServico.AGUARDANDO_APROVACAO);
  }
}
