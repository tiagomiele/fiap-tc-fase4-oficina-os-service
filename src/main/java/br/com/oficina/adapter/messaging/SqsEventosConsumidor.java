package br.com.oficina.adapter.messaging;

import br.com.oficina.usecase.SagaOrdemServicoServiceImpl;
import br.com.oficina.usecase.saga.MensagemSaga;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * Lê a fila {@code os-eventos} com long polling. A mensagem só é apagada depois de processada; em
 * caso de erro ela volta à fila e, após o {@code maxReceiveCount}, vai para a DLQ.
 */
@Component
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "sqs")
public class SqsEventosConsumidor {

  private static final Logger log = LoggerFactory.getLogger(SqsEventosConsumidor.class);

  private final SqsClient sqs;
  private final ObjectMapper mapper;
  private final SagaOrdemServicoServiceImpl saga;
  private final String fila;
  private final int maxMensagens;
  private final int esperaSegundos;

  public SqsEventosConsumidor(
      SqsClient sqs,
      ObjectMapper mapper,
      SagaOrdemServicoServiceImpl saga,
      @Value("${oficina.mensageria.sqs.os-eventos-url}") String fila,
      @Value("${oficina.mensageria.sqs.max-messages:10}") int maxMensagens,
      @Value("${oficina.mensageria.sqs.wait-time-seconds:20}") int esperaSegundos) {
    this.sqs = sqs;
    this.mapper = mapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.saga = saga;
    this.fila = fila;
    this.maxMensagens = maxMensagens;
    this.esperaSegundos = esperaSegundos;
  }

  @Scheduled(fixedDelayString = "${oficina.mensageria.sqs.poll-delay-ms:1000}")
  public void consumir() {
    if (fila == null || fila.isBlank()) {
      return;
    }
    sqs.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(fila)
                .maxNumberOfMessages(maxMensagens)
                .waitTimeSeconds(esperaSegundos)
                .build())
        .messages()
        .forEach(this::tratar);
  }

  boolean tratar(Message message) {
    MensagemSaga evento;
    try {
      evento = mapper.readValue(message.body(), MensagemSaga.class);
    } catch (Exception e) {
      log.error(
          "Mensagem SQS {} inválida; segue para a DLQ após as tentativas", message.messageId());
      return false;
    }
    CorrelationIds.abrir(evento);
    try {
      saga.processar(evento);
      sqs.deleteMessage(
          DeleteMessageRequest.builder()
              .queueUrl(fila)
              .receiptHandle(message.receiptHandle())
              .build());
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Falha ao processar {} da OS {}: {}", evento.tipo(), evento.numeroOs(), e.getMessage());
      return false;
    } finally {
      CorrelationIds.fechar();
    }
  }
}
