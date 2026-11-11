package br.com.oficina.integration;

import br.com.oficina.adapter.messaging.MemoriaMensageriaGateway;
import br.com.oficina.usecase.SagaOrdemServicoServiceImpl;
import br.com.oficina.usecase.saga.MensagemSaga;
import br.com.oficina.usecase.saga.TipoMensagemSaga;
import io.jsonwebtoken.Jwts;
import io.restassured.RestAssured;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

  private static final KeyPair CLIENT_KEY_PAIR = generateClientKeyPair();

  @LocalServerPort private int port;

  @Autowired protected SagaOrdemServicoServiceImpl saga;

  @Autowired(required = false)
  protected MemoriaMensageriaGateway mensageriaMemoria;

  @DynamicPropertySource
  static void clientJwtProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "oficina.security.serverless-jwt.public-key",
        () ->
            "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'})
                    .encodeToString(CLIENT_KEY_PAIR.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");
  }

  @BeforeEach
  void setUpRestAssured() {
    RestAssured.port = port;
    RestAssured.basePath = "";
  }

  protected String loginComoAdmin() {
    return loginComo("admin@oficina.local", "admin123");
  }

  protected String loginComo(String email, String senha) {
    return RestAssured.given()
        .contentType("application/json")
        .body("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}")
        .post("/auth/login")
        .then()
        .statusCode(200)
        .extract()
        .path("accessToken");
  }

  protected String tokenCliente(Long idCliente) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(idCliente.toString())
        .claim("client_id", idCliente)
        .claim("role", "CLIENTE")
        .claim("aud", "oficina-backend")
        .issuer("fiap-tc-fase4-oficina-auth-serverless")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(900)))
        .signWith(CLIENT_KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  private static KeyPair generateClientKeyPair() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao gerar chaves de teste", exception);
    }
  }

  /** Simula um evento publicado por outro serviço na fila os-eventos. */
  protected boolean evento(TipoMensagemSaga tipo, String numeroOs, Object... payload) {
    return saga.processar(mensagem(tipo, numeroOs, payload));
  }

  protected MensagemSaga mensagem(TipoMensagemSaga tipo, String numeroOs, Object... payload) {
    return new MensagemSaga(
        UUID.randomUUID().toString(),
        tipo,
        "corr-" + numeroOs,
        numeroOs,
        "teste",
        Instant.now(),
        MensagemSaga.dados(payload));
  }

  protected void assertStatus(String numeroOs, String status) {
    RestAssured.given()
        .header("Authorization", "Bearer " + loginComoAdmin())
        .get("/ordens-servico/" + numeroOs)
        .then()
        .statusCode(200)
        .body("status", org.hamcrest.Matchers.equalTo(status));
  }
}
