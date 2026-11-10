package br.com.oficina.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.usecase.OrdemServicoServiceImpl;
import br.com.oficina.usecase.saga.MensagemSaga;
import br.com.oficina.usecase.saga.TipoMensagemSaga;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/** Saga ponta a ponta no SQS do LocalStack: comando publicado e evento consumido. */
class SqsLocalStackIntegrationTest extends IntegrationTestBase {

  private static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
          .withServices(LocalStackContainer.Service.SQS);
  private static final SqsClient SQS;
  private static final String OS_EVENTOS;
  private static final String DIAGNOSTICO_REPARO;
  private static final String ORCAMENTO_PAGAMENTO;

  static {
    LOCALSTACK.start();
    SQS =
        SqsClient.builder()
            .endpointOverride(LOCALSTACK.getEndpoint())
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .build();
    OS_EVENTOS = SQS.createQueue(b -> b.queueName("os-eventos")).queueUrl();
    DIAGNOSTICO_REPARO =
        SQS.createQueue(b -> b.queueName("diagnostico-reparo-comandos")).queueUrl();
    ORCAMENTO_PAGAMENTO =
        SQS.createQueue(b -> b.queueName("orcamento-pagamento-comandos")).queueUrl();
  }

  @DynamicPropertySource
  static void sqsProperties(DynamicPropertyRegistry registry) {
    registry.add("oficina.mensageria.tipo", () -> "sqs");
    registry.add("oficina.mensageria.sqs.endpoint", () -> LOCALSTACK.getEndpoint().toString());
    registry.add("oficina.mensageria.sqs.region", LOCALSTACK::getRegion);
    registry.add("oficina.mensageria.sqs.os-eventos-url", () -> OS_EVENTOS);
    registry.add(
        "oficina.mensageria.sqs.diagnostico-reparo-comandos-url", () -> DIAGNOSTICO_REPARO);
    registry.add(
        "oficina.mensageria.sqs.orcamento-pagamento-comandos-url", () -> ORCAMENTO_PAGAMENTO);
    registry.add("oficina.mensageria.sqs.wait-time-seconds", () -> "1");
    registry.add("oficina.mensageria.sqs.poll-delay-ms", () -> "200");
  }

  @Autowired private ObjectMapper mapper;
  @Autowired private OrdemServicoServiceImpl ordens;

  @Test
  void publicaComandoEConsomeEventoPeloSqs() throws Exception {
    String token = loginComoAdmin();
    Long idCliente =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                Map.of(
                    "nome", "Cliente SQS",
                    "documento", "03365020284",
                    "email", "sqs@teste.com",
                    "telefone", "11999999999"))
            .post("clientes")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("idCliente");
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            Map.of(
                "placa",
                "SQS4D44",
                "marca",
                "Fiat",
                "modelo",
                "Uno",
                "ano",
                2020,
                "idCliente",
                idCliente))
        .post("veiculos")
        .then()
        .statusCode(201);
    String numeroOs =
        given()
            .header("Authorization", "Bearer " + token)
            .header("X-Request-Id", "saga-sqs-1")
            .contentType("application/json")
            .body(Map.of("idCliente", idCliente, "placa", "SQS4D44", "descricaoProblema", "Teste"))
            .post("ordens-servico")
            .then()
            .statusCode(201)
            .extract()
            .path("numero");

    List<Message> recebidas =
        await()
            .atMost(Duration.ofSeconds(20))
            .until(
                () ->
                    SQS.receiveMessage(b -> b.queueUrl(DIAGNOSTICO_REPARO).waitTimeSeconds(1))
                        .messages(),
                m -> !m.isEmpty());
    MensagemSaga comando = mapper.readValue(recebidas.get(0).body(), MensagemSaga.class);
    assertThat(comando.tipo()).isEqualTo(TipoMensagemSaga.INICIAR_DIAGNOSTICO);
    assertThat(comando.numeroOs()).isEqualTo(numeroOs);
    assertThat(comando.correlationId()).isEqualTo("saga-sqs-1");

    MensagemSaga evento =
        new MensagemSaga(
            UUID.randomUUID().toString(),
            TipoMensagemSaga.DIAGNOSTICO_INICIADO,
            comando.correlationId(),
            numeroOs,
            "fiap-tc-fase4-oficina-diagnostico-reparo-service",
            Instant.now(),
            Map.of());
    String corpo = json(evento);
    SQS.sendMessage(b -> b.queueUrl(OS_EVENTOS).messageBody(corpo));

    await()
        .atMost(Duration.ofSeconds(20))
        .until(() -> ordens.consultar(numeroOs).getStatus() == StatusOrdemServico.EM_DIAGNOSTICO);
  }

  private String json(MensagemSaga mensagem) throws Exception {
    return mapper.writeValueAsString(mensagem);
  }
}
