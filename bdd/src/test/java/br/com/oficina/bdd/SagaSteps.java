package br.com.oficina.bdd;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.jsonwebtoken.Jwts;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Passos da Saga. Cada cenário cria os próprios clientes, peça e OS. */
public class SagaSteps {

  private static final String OS = System.getProperty("bdd.os-url", "http://localhost:8081");
  private static final String DR = System.getProperty("bdd.dr-url", "http://localhost:8082");
  private static final String OP = System.getProperty("bdd.op-url", "http://localhost:8083");
  private static final Duration ESPERA = Duration.ofSeconds(60);

  private static PrivateKey chaveCliente;

  private String tokenFuncionario;
  private Long idPeca;
  private final Map<String, String> numeroOs = new HashMap<>();
  private final Map<String, Long> clienteDaOs = new HashMap<>();
  private final Map<String, String> correlationDaOs = new HashMap<>();

  @Before
  public void login() {
    tokenFuncionario =
        given()
            .baseUri(OS)
            .contentType("application/json")
            .body(
                Map.of(
                    "email", System.getProperty("bdd.admin-email", "admin@oficina.local"),
                    "senha", System.getProperty("bdd.admin-password", "admin123")))
            .post("/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("accessToken");
  }

  @Dado("uma peça com estoque {int}")
  public void umaPecaComEstoque(int quantidade) {
    idPeca =
        funcionario(OS)
            .body(Map.of("nome", "Filtro BDD " + sufixo(), "precoVenda", 40.00))
            .post("/pecas")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("idSku");
    funcionario(OS)
        .body(
            Map.of(
                "numeroNota", "BDD" + sufixo(),
                "serieNota", "1",
                "cnpjFornecedor", "11222333000181",
                "dataEmissao", "2026-05-01",
                "nomeFornecedor", "Fornecedor BDD",
                "itens",
                    List.of(
                        Map.of("idSku", idPeca, "quantidade", quantidade, "precoUnitario", 20.00))))
        .post("/notas-fiscais-fornecedor")
        .then()
        .statusCode(201);
  }

  @Dado("uma OS {string} em diagnóstico com {int} unidade(s) da peça")
  public void umaOsEmDiagnostico(String alias, int quantidade) {
    Long idCliente = cadastrarClienteComVeiculo();
    String placa = placaDoCliente.get(idCliente);
    Long idServico =
        funcionario(OS)
            .body(Map.of("nome", "Revisão BDD " + sufixo(), "descricao", "Revisão", "precoBase", 150.00))
            .post("/servicos")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("idServico");
    String correlationId = "bdd-" + sufixo();
    String numero =
        funcionario(OS)
            .header("X-Request-Id", correlationId)
            .body(Map.of("idCliente", idCliente, "placa", placa, "descricaoProblema", "Barulho"))
            .post("/ordens-servico")
            .then()
            .statusCode(201)
            .extract()
            .path("numero");
    numeroOs.put(alias, numero);
    clienteDaOs.put(alias, idCliente);
    correlationDaOs.put(alias, correlationId);

    aExecucaoFica(alias, "NA_FILA_DIAGNOSTICO");
    funcionario(DR).post("/execucoes/" + numero + "/iniciar-diagnostico").then().statusCode(200);
    aOsFica(alias, "EM_DIAGNOSTICO");

    funcionario(OS)
        .body(Map.of("idServicoSku", idServico, "quantidade", 1))
        .post("/ordens-servico/" + numero + "/servicos")
        .then()
        .statusCode(200);
    funcionario(OS)
        .body(Map.of("idServicoSku", idPeca, "quantidade", quantidade))
        .post("/ordens-servico/" + numero + "/pecas")
        .then()
        .statusCode(200);
  }

  @Quando("o técnico conclui o diagnóstico da OS {string}")
  public void concluiDiagnostico(String alias) {
    funcionario(DR)
        .body(Map.of("diagnostico", "Troca do filtro"))
        .post("/execucoes/" + os(alias) + "/concluir-diagnostico")
        .then()
        .statusCode(200);
  }

  @Quando("o cliente aprova o orçamento da OS {string}")
  public void clienteAprova(String alias) {
    cliente(alias).post("/orcamentos/" + os(alias) + "/aprovar").then().statusCode(200);
  }

  @Quando("o cliente rejeita o orçamento da OS {string} com a ação {string}")
  public void clienteRejeita(String alias, String acao) {
    cliente(alias)
        .body(Map.of("acao", acao, "motivo", "Teste BDD"))
        .post("/orcamentos/" + os(alias) + "/rejeitar")
        .then()
        .statusCode(200);
  }

  @Quando("o técnico executa o reparo da OS {string}")
  public void executaReparo(String alias) {
    aExecucaoFica(alias, "NA_FILA_REPARO");
    funcionario(DR).post("/execucoes/" + os(alias) + "/iniciar-reparo").then().statusCode(200);
    funcionario(DR).post("/execucoes/" + os(alias) + "/concluir-reparo").then().statusCode(200);
  }

  @Quando("o pagamento da OS {string} é aprovado")
  public void pagamentoAprovado(String alias) {
    simularPagamento(alias, true);
  }

  @Quando("o pagamento da OS {string} é recusado")
  public void pagamentoRecusado(String alias) {
    simularPagamento(alias, false);
  }

  @Quando("o cliente pede nova tentativa de pagamento da OS {string}")
  public void novaTentativa(String alias) {
    cliente(alias).post("/pagamentos/" + os(alias) + "/nova-tentativa").then().statusCode(200);
  }

  @Quando("a OS {string} é entregue")
  public void entregar(String alias) {
    funcionario(OS).post("/ordens-servico/" + os(alias) + "/entregar").then().statusCode(200);
  }

  @Então("a OS {string} fica {string}")
  public void aOsFica(String alias, String status) {
    aguardar(() -> funcionario(OS).get("/ordens-servico/" + os(alias)), "status", status);
  }

  @Então("a OS {string} continua {string}")
  public void aOsContinua(String alias, String status) {
    aOsFica(alias, status);
  }

  @Então("o orçamento da OS {string} fica {string}")
  public void oOrcamentoFica(String alias, String status) {
    aguardar(() -> cliente(alias).get("/orcamentos/" + os(alias)), "status", status);
  }

  @Então("o pagamento da OS {string} fica {string}")
  public void oPagamentoFica(String alias, String status) {
    aguardar(() -> cliente(alias).get("/pagamentos/" + os(alias)), "status", status);
  }

  @Então("a execução da OS {string} fica {string}")
  public void aExecucaoFica(String alias, String etapa) {
    aguardar(() -> funcionario(DR).get("/execucoes/" + os(alias)), "etapa", etapa);
  }

  @E("o saldo da peça é {int}")
  public void oSaldoDaPeca(int saldo) {
    await()
        .atMost(ESPERA)
        .untilAsserted(
            () ->
                assertThat(
                        funcionario(OS)
                            .get("/estoque")
                            .then()
                            .statusCode(200)
                            .extract()
                            .jsonPath()
                            .getInt("find { it.idSku == " + idPeca + " }.quantidade"))
                    .isEqualTo(saldo));
  }

  @E("todas as etapas da Saga no histórico da OS {string} têm o correlationId da abertura")
  public void historicoComCorrelationId(String alias) {
    List<String> ids =
        cliente(alias)
            .baseUri(OS)
            .get("/ordens-servico/" + os(alias) + "/historico")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("correlationId", String.class);
    assertThat(ids).hasSizeGreaterThanOrEqualTo(6).containsOnly(correlationDaOs.get(alias));
  }

  private void simularPagamento(String alias, boolean aprovado) {
    oPagamentoFica(alias, "PENDENTE");
    funcionario(OP)
        .queryParam("aprovado", aprovado)
        .post("/pagamentos/" + os(alias) + "/simular")
        .then()
        .statusCode(200);
  }

  private final Map<Long, String> placaDoCliente = new HashMap<>();

  private Long cadastrarClienteComVeiculo() {
    String cpf = cpf();
    Long idCliente =
        funcionario(OS)
            .body(
                Map.of(
                    "nome", "Cliente BDD",
                    "documento", cpf,
                    "email", "bdd" + cpf + "@teste.com",
                    "telefone", "11999999999"))
            .post("/clientes")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("idCliente");
    String placa = "BDD" + ThreadLocalRandom.current().nextInt(1000, 10000);
    funcionario(OS)
        .body(
            Map.of(
                "placa", placa, "marca", "Fiat", "modelo", "Uno", "ano", 2020,
                "idCliente", idCliente))
        .post("/veiculos")
        .then()
        .statusCode(201);
    placaDoCliente.put(idCliente, placa);
    return idCliente;
  }

  private void aguardar(java.util.function.Supplier<Response> chamada, String campo, String valor) {
    await()
        .atMost(ESPERA)
        .pollInterval(Duration.ofMillis(500))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              Response resposta = chamada.get();
              assertThat(resposta.statusCode()).isEqualTo(200);
              assertThat(resposta.jsonPath().getString(campo)).isEqualTo(valor);
            });
  }

  private String os(String alias) {
    return numeroOs.get(alias);
  }

  private RequestSpecification funcionario(String baseUri) {
    return given()
        .baseUri(baseUri)
        .contentType("application/json")
        .header("Authorization", "Bearer " + tokenFuncionario);
  }

  private RequestSpecification cliente(String alias) {
    Long idCliente = clienteDaOs.get(alias);
    Instant agora = Instant.now();
    String token =
        Jwts.builder()
            .subject(idCliente.toString())
            .claim("client_id", idCliente)
            .claim("role", "CLIENTE")
            .claim("aud", "oficina-backend")
            .issuer("fiap-tc-fase4-oficina-auth-serverless")
            .issuedAt(Date.from(agora))
            .expiration(Date.from(agora.plusSeconds(900)))
            .signWith(chaveCliente(), Jwts.SIG.RS256)
            .compact();
    return given()
        .baseUri(OP)
        .contentType("application/json")
        .header("Authorization", "Bearer " + token);
  }

  private static synchronized PrivateKey chaveCliente() {
    if (chaveCliente == null) {
      try {
        String pem =
            Files.readString(
                Path.of(
                    System.getProperty(
                        "bdd.cliente-private-key", "target/keys/cliente-private.pem")));
        String base64 =
            pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        chaveCliente =
            KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
      } catch (Exception e) {
        throw new IllegalStateException("Chave privada do cliente do BDD não encontrada", e);
      }
    }
    return chaveCliente;
  }

  private static String sufixo() {
    return Long.toString(System.nanoTime(), 36);
  }

  /** Gera um CPF válido e aleatório (os clientes não podem repetir documento). */
  private static String cpf() {
    int[] d = new int[11];
    for (int i = 0; i < 9; i++) {
      d[i] = ThreadLocalRandom.current().nextInt(10);
    }
    for (int j = 9; j < 11; j++) {
      int soma = 0;
      for (int i = 0; i < j; i++) {
        soma += d[i] * (j + 1 - i);
      }
      int resto = (soma * 10) % 11;
      d[j] = resto == 10 ? 0 : resto;
    }
    StringBuilder sb = new StringBuilder();
    for (int n : d) {
      sb.append(n);
    }
    return sb.toString();
  }
}
