package br.com.oficina.adapter.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.oficina.usecase.gateway.NotificacaoGateway;
import br.com.oficina.usecase.gateway.ObservabilidadeGateway;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NotificacaoGatewayTest {

  @Test
  void notificacaoSimuladaNaoRegistraDadosPessoais() {
    Logger logger = (Logger) LoggerFactory.getLogger(LogNotificacaoGateway.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      new LogNotificacaoGateway()
          .enviar(
              "cliente@example.com",
              "OS 2026-000001 — PAGA",
              "Cliente CPF 52998224725 teve a OS atualizada");
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.getFirst().getFormattedMessage())
        .isEqualTo("Notificacao simulada processada")
        .doesNotContain("cliente@example.com", "52998224725", "2026-000001");
  }

  @Test
  void notificacaoServerlessEnviaPayloadAutenticado() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    ObservabilidadeGateway observabilidade = mock(ObservabilidadeGateway.class);
    server
        .expect(once(), requestTo("https://notifications.example.com"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Notification-Key", "secret-key"))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "destinatario": "cliente@example.com",
                      "assunto": "OS 2026-000001",
                      "corpo": "Status atualizado"
                    }
                    """))
        .andRespond(withSuccess());
    ServerlessNotificacaoGateway gateway =
        new ServerlessNotificacaoGateway(
            builder
                .baseUrl("https://notifications.example.com")
                .defaultHeader("X-Notification-Key", "secret-key")
                .build(),
            observabilidade);

    gateway.enviar("cliente@example.com", "OS 2026-000001", "Status atualizado");

    server.verify();
  }

  @Test
  void falhaServerlessGeraEventoTecnicoSemPropagarErro() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    ObservabilidadeGateway observabilidade = mock(ObservabilidadeGateway.class);
    server
        .expect(once(), requestTo("https://notifications.example.com"))
        .andRespond(withServerError());
    ServerlessNotificacaoGateway gateway =
        new ServerlessNotificacaoGateway(
            builder
                .baseUrl("https://notifications.example.com")
                .defaultHeader("X-Notification-Key", "secret-key")
                .build(),
            observabilidade);

    gateway.enviar("cliente@example.com", "OS 2026-000001", "Status atualizado");

    verify(observabilidade)
        .integracaoExternaFalhou(
            "serverless-notification", "enfileirar-notificacao", "InternalServerError");
    server.verify();
  }

  @Test
  void notificacaoServerlessNaoBloqueiaFluxoPrincipal() throws NoSuchMethodException {
    assertThat(
            AnnotatedElementUtils.findMergedAnnotation(
                ServerlessNotificacaoGateway.class.getMethod(
                    "enviar", String.class, String.class, String.class),
                Async.class))
        .isNotNull();
  }

  @Test
  void contextoServerlessInstanciaGatewayComConstrutorDeProducao() {
    new ApplicationContextRunner()
        .withInitializer(
            context ->
                context
                    .getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()))
        .withBean(RestClient.Builder.class, RestClient::builder)
        .withBean(ObservabilidadeGateway.class, () -> mock(ObservabilidadeGateway.class))
        .withPropertyValues(
            "oficina.notificacao.tipo=serverless",
            "oficina.notificacao.serverless.endpoint=https://notifications.example.com",
            "oficina.notificacao.serverless.api-key=secret-key")
        .withUserConfiguration(ServerlessNotificacaoGateway.class)
        .run(context -> assertThat(context).hasSingleBean(NotificacaoGateway.class));
  }

  @Test
  void falhaSmtpGeraEventoTecnicoSemPropagarDadosPessoais() {
    JavaMailSender mailSender = mock(JavaMailSender.class);
    ObservabilidadeGateway observabilidade = mock(ObservabilidadeGateway.class);
    MailSendException falha = new MailSendException("falha para cliente@example.com");
    org.mockito.Mockito.doThrow(falha)
        .when(mailSender)
        .send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    SmtpNotificacaoGateway gateway =
        new SmtpNotificacaoGateway(mailSender, observabilidade, "nao-responder@oficina.local");

    gateway.enviar(
        "cliente@example.com", "OS 2026-000001", "CPF 52998224725 e outros dados privados");

    verify(observabilidade)
        .integracaoExternaFalhou("smtp", "enviar-notificacao", "MailSendException");
  }
}
