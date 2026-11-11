package br.com.oficina.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.adapter.exception.RequestIdFilter;
import br.com.oficina.usecase.SagaOrdemServicoServiceImpl;
import br.com.oficina.usecase.saga.MensagemSaga;
import br.com.oficina.usecase.saga.TipoMensagemSaga;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class SqsMensageriaTest {

  private final SqsClient sqs = mock(SqsClient.class);
  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @AfterEach
  void limpar() {
    MDC.clear();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private SqsMensageriaGateway gateway(String fila) {
    return new SqsMensageriaGateway(sqs, mapper, fila, "http://orcamento");
  }

  @Test
  void enviaParaFilaDoDestinoComCorrelationIdDoMdc() throws Exception {
    MDC.put(RequestIdFilter.MDC_KEY, "req-1");
    gateway("http://diagnostico")
        .enviar(MensagemSaga.comando(TipoMensagemSaga.INICIAR_DIAGNOSTICO, "OS-1", null));

    ArgumentCaptor<SendMessageRequest> req = ArgumentCaptor.forClass(SendMessageRequest.class);
    verify(sqs).sendMessage(req.capture());
    assertThat(req.getValue().queueUrl()).isEqualTo("http://diagnostico");
    assertThat(req.getValue().messageAttributes().get("correlationId").stringValue())
        .isEqualTo("req-1");
    MensagemSaga enviada = mapper.readValue(req.getValue().messageBody(), MensagemSaga.class);
    assertThat(enviada.tipo()).isEqualTo(TipoMensagemSaga.INICIAR_DIAGNOSTICO);
    assertThat(enviada.correlationId()).isEqualTo("req-1");
  }

  @Test
  void dentroDeTransacaoSoEnviaDepoisDoCommit() {
    TransactionSynchronizationManager.initSynchronization();
    gateway("http://diagnostico")
        .enviar(MensagemSaga.comando(TipoMensagemSaga.INICIAR_REPARO, "OS-1", null));
    verify(sqs, never()).sendMessage(any(SendMessageRequest.class));

    TransactionSynchronizationUtils.invokeAfterCommit(
        TransactionSynchronizationManager.getSynchronizations());
    verify(sqs).sendMessage(any(SendMessageRequest.class));
  }

  @Test
  void falhaQuandoFilaNaoConfigurada() {
    MensagemSaga m = MensagemSaga.comando(TipoMensagemSaga.INICIAR_REPARO, "OS-1", null);
    assertThatThrownBy(() -> gateway("").enviar(m)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void consumidorProcessaEApagaMensagem() throws Exception {
    SagaOrdemServicoServiceImpl saga = mock(SagaOrdemServicoServiceImpl.class);
    SqsEventosConsumidor consumidor =
        new SqsEventosConsumidor(sqs, mapper, saga, "http://os-eventos", 10, 0);
    MensagemSaga evento =
        new MensagemSaga(
            "m-1", TipoMensagemSaga.DIAGNOSTICO_INICIADO, "c-1", "OS-1", "teste", null, null);
    Message ok =
        Message.builder()
            .messageId("m-1")
            .receiptHandle("r-1")
            .body(mapper.writeValueAsString(evento))
            .build();
    Message invalida = Message.builder().messageId("m-2").receiptHandle("r-2").body("{").build();
    when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
        .thenReturn(ReceiveMessageResponse.builder().messages(List.of(ok, invalida)).build());

    consumidor.consumir();

    verify(saga).processar(any(MensagemSaga.class));
    ArgumentCaptor<DeleteMessageRequest> del = ArgumentCaptor.forClass(DeleteMessageRequest.class);
    verify(sqs).deleteMessage(del.capture());
    assertThat(del.getValue().receiptHandle()).isEqualTo("r-1");
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void consumidorNaoApagaQuandoProcessamentoFalha() throws Exception {
    SagaOrdemServicoServiceImpl saga = mock(SagaOrdemServicoServiceImpl.class);
    when(saga.processar(any())).thenThrow(new IllegalStateException("erro"));
    SqsEventosConsumidor consumidor =
        new SqsEventosConsumidor(sqs, mapper, saga, "http://os-eventos", 10, 0);
    MensagemSaga evento =
        new MensagemSaga(
            "m-1", TipoMensagemSaga.REPARO_CONCLUIDO, null, "OS-1", "teste", null, null);
    Message msg =
        Message.builder().receiptHandle("r-1").body(mapper.writeValueAsString(evento)).build();

    assertThat(consumidor.tratar(msg)).isFalse();
    verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
  }

  @Test
  void consumidorSemFilaNaoFazNada() {
    new SqsEventosConsumidor(sqs, mapper, mock(SagaOrdemServicoServiceImpl.class), "", 10, 0)
        .consumir();
    verify(sqs, never()).receiveMessage(any(ReceiveMessageRequest.class));
  }

  @Test
  void memoriaGuardaUltimasMensagens() {
    MemoriaMensageriaGateway memoria = new MemoriaMensageriaGateway();
    for (int i = 0; i <= MemoriaMensageriaGateway.LIMITE; i++) {
      memoria.enviar(MensagemSaga.comando(TipoMensagemSaga.INICIAR_REPARO, "OS-" + i, null));
    }
    assertThat(memoria.enviadas()).hasSize(MemoriaMensageriaGateway.LIMITE);
    assertThat(memoria.enviadasPara("OS-0")).isEmpty();
    assertThat(memoria.enviadasPara("OS-1").get(0).correlationId()).isNotBlank();
  }
}
