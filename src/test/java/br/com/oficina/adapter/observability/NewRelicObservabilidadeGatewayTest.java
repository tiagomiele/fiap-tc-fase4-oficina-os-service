package br.com.oficina.adapter.observability;

import static org.assertj.core.api.Assertions.assertThatCode;

import br.com.oficina.domain.enums.StatusOrdemServico;
import org.junit.jupiter.api.Test;

class NewRelicObservabilidadeGatewayTest {

  @Test
  void permaneceNoOpSemAgenteQuandoDesabilitado() {
    NewRelicObservabilidadeGateway gateway = new NewRelicObservabilidadeGateway(false, "test");

    assertThatCode(
            () -> {
              gateway.ordemServicoCriada("OS-082026-000001", StatusOrdemServico.RECEBIDA);
              gateway.ordemServicoStatusAlterado(
                  "OS-082026-000001",
                  StatusOrdemServico.RECEBIDA,
                  StatusOrdemServico.EM_DIAGNOSTICO,
                  100L);
              gateway.ordemServicoProcessamentoFalhou("OS-082026-000001", "POST", "ERRO_INTERNO");
              gateway.integracaoExternaFalhou("smtp", "enviar-notificacao", "MailSendException");
            })
        .doesNotThrowAnyException();
  }
}
