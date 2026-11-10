package br.com.oficina.adapter.notification;

import br.com.oficina.usecase.gateway.NotificacaoGateway;
import br.com.oficina.usecase.gateway.ObservabilidadeGateway;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnProperty(name = "oficina.notificacao.tipo", havingValue = "serverless")
public class ServerlessNotificacaoGateway implements NotificacaoGateway {

  private static final Logger log = LoggerFactory.getLogger(ServerlessNotificacaoGateway.class);
  private static final String API_KEY_HEADER = "X-Notification-Key";

  private final RestClient client;
  private final ObservabilidadeGateway observabilidade;

  @Autowired
  public ServerlessNotificacaoGateway(
      RestClient.Builder builder,
      ObservabilidadeGateway observabilidade,
      @Value("${oficina.notificacao.serverless.endpoint}") String endpoint,
      @Value("${oficina.notificacao.serverless.api-key}") String apiKey,
      @Value("${oficina.notificacao.serverless.connect-timeout:2s}") Duration connectTimeout,
      @Value("${oficina.notificacao.serverless.read-timeout:12s}") Duration readTimeout) {
    this(
        notificationClient(builder, endpoint, apiKey, connectTimeout, readTimeout),
        observabilidade);
  }

  ServerlessNotificacaoGateway(RestClient client, ObservabilidadeGateway observabilidade) {
    this.client = client;
    this.observabilidade = observabilidade;
  }

  @Override
  @Async
  public void enviar(String destinatario, String assunto, String corpo) {
    try {
      client
          .post()
          .contentType(MediaType.APPLICATION_JSON)
          .body(new NotificacaoRequest(destinatario, assunto, corpo))
          .retrieve()
          .toBodilessEntity();
      log.info("Notificacao serverless aceita para processamento");
    } catch (RestClientException ex) {
      observabilidade.integracaoExternaFalhou(
          "serverless-notification", "enfileirar-notificacao", ex.getClass().getSimpleName());
      log.error(
          "Falha na integracao serverless de notificacao: tipoErro={}",
          ex.getClass().getSimpleName());
    }
  }

  private static RestClient notificationClient(
      RestClient.Builder builder,
      String endpoint,
      String apiKey,
      Duration connectTimeout,
      Duration readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);
    return builder
        .requestFactory(requestFactory)
        .baseUrl(endpoint)
        .defaultHeader(API_KEY_HEADER, apiKey)
        .build();
  }

  private record NotificacaoRequest(String destinatario, String assunto, String corpo) {}
}
