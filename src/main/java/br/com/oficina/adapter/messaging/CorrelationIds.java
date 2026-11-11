package br.com.oficina.adapter.messaging;

import br.com.oficina.adapter.exception.RequestIdFilter;
import br.com.oficina.usecase.saga.MensagemSaga;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * O {@code correlationId} da Saga usa a mesma chave de MDC do {@code X-Request-Id}: nasce na
 * requisição que abriu a OS e é repassado em todos os comandos e eventos seguintes.
 */
final class CorrelationIds {

  static final String MDC_CORRELATION_ID = "correlationId";

  private CorrelationIds() {}

  static MensagemSaga aplicar(MensagemSaga mensagem) {
    if (mensagem.correlationId() != null && !mensagem.correlationId().isBlank()) {
      return mensagem;
    }
    String atual = MDC.get(RequestIdFilter.MDC_KEY);
    return mensagem.comCorrelationId(
        atual == null || atual.isBlank() ? UUID.randomUUID().toString() : atual);
  }

  static void abrir(MensagemSaga mensagem) {
    String id =
        mensagem.correlationId() == null || mensagem.correlationId().isBlank()
            ? mensagem.messageId()
            : mensagem.correlationId();
    MDC.put(RequestIdFilter.MDC_KEY, id);
    MDC.put(MDC_CORRELATION_ID, id);
  }

  static void fechar() {
    MDC.remove(RequestIdFilter.MDC_KEY);
    MDC.remove(MDC_CORRELATION_ID);
  }
}
