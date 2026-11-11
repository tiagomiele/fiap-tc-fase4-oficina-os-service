package br.com.oficina.adapter.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.oficina.usecase.gateway.ObservabilidadeGateway;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

class GlobalExceptionHandlerObservabilidadeTest {

  @Test
  void erroGenericoGeraEventoSemRegistrarMensagemPotencialmenteSensivel() {
    ObservabilidadeGateway observabilidade = mock(ObservabilidadeGateway.class);
    GlobalExceptionHandler handler = new GlobalExceptionHandler(observabilidade);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/ordens-servico/OS-082026-000001/aprovar");
    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      handler.handleGeneric(
          new IllegalStateException("CPF 52998224725 token segredo-interno"), request);
    } finally {
      logger.detachAppender(appender);
    }

    verify(observabilidade)
        .ordemServicoProcessamentoFalhou("OS-082026-000001", "POST", "ERRO_INTERNO");
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.getFirst().getFormattedMessage())
        .isEqualTo("Erro não tratado: tipoErro=IllegalStateException")
        .doesNotContain("52998224725", "segredo-interno");
  }
}
