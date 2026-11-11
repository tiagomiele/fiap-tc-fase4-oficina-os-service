package br.com.oficina.adapter.messaging;

import br.com.oficina.usecase.gateway.MensageriaGateway;
import br.com.oficina.usecase.saga.DestinoMensagem;
import br.com.oficina.usecase.saga.MensagemSaga;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Envia os comandos da Saga para a fila SQS do serviço de destino. Dentro de uma transação, o envio
 * acontece só depois do commit, para não publicar um passo que foi desfeito no banco.
 */
@Component
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "sqs")
public class SqsMensageriaGateway implements MensageriaGateway {

  private static final Logger log = LoggerFactory.getLogger(SqsMensageriaGateway.class);

  private final SqsClient sqs;
  private final ObjectMapper mapper;
  private final Map<DestinoMensagem, String> filas;

  public SqsMensageriaGateway(
      SqsClient sqs,
      ObjectMapper mapper,
      @Value("${oficina.mensageria.sqs.diagnostico-reparo-comandos-url}") String diagnosticoReparo,
      @Value("${oficina.mensageria.sqs.orcamento-pagamento-comandos-url}")
          String orcamentoPagamento) {
    this.sqs = sqs;
    this.mapper = mapper;
    this.filas =
        Map.of(
            DestinoMensagem.DIAGNOSTICO_REPARO, diagnosticoReparo,
            DestinoMensagem.ORCAMENTO_PAGAMENTO, orcamentoPagamento);
  }

  @Override
  public void enviar(MensagemSaga mensagem) {
    MensagemSaga comCorrelacao = CorrelationIds.aplicar(mensagem);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              publicar(comCorrelacao);
            }
          });
      return;
    }
    publicar(comCorrelacao);
  }

  void publicar(MensagemSaga mensagem) {
    String fila = filas.get(mensagem.tipo().destino());
    if (fila == null || fila.isBlank()) {
      throw new IllegalStateException(
          "URL da fila SQS não configurada para " + mensagem.tipo().destino());
    }
    sqs.sendMessage(
        SendMessageRequest.builder()
            .queueUrl(fila)
            .messageBody(json(mensagem))
            .messageAttributes(
                Map.of(
                    "tipo", atributo(mensagem.tipo().name()),
                    "correlationId", atributo(mensagem.correlationId())))
            .build());
    log.info(
        "Comando {} da OS {} enviado para {} (messageId {})",
        mensagem.tipo(),
        mensagem.numeroOs(),
        mensagem.tipo().destino(),
        mensagem.messageId());
  }

  private String json(MensagemSaga mensagem) {
    try {
      return mapper.writeValueAsString(mensagem);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Falha ao serializar mensagem da Saga", e);
    }
  }

  private static MessageAttributeValue atributo(String valor) {
    return MessageAttributeValue.builder().dataType("String").stringValue(valor).build();
  }
}
