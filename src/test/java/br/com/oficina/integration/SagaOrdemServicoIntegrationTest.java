package br.com.oficina.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import br.com.oficina.usecase.saga.MensagemSaga;
import br.com.oficina.usecase.saga.TipoMensagemSaga;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SagaOrdemServicoIntegrationTest extends IntegrationTestBase {

  @Test
  void fluxoFelizComPecaIdempotenciaEPagamentoRecusado() {
    String token = loginComoAdmin();
    Long idCliente = cadastrarCliente(token, "93857083921", "SAG1A11");
    Long idSku = cadastrarPecaComEstoque(token, "Vela", 2, "9001");
    String os = abrirOs(token, idCliente, "SAG1A11");

    assertThat(tipos(os)).containsExactly(TipoMensagemSaga.INICIAR_DIAGNOSTICO);

    MensagemSaga iniciado = mensagem(TipoMensagemSaga.DIAGNOSTICO_INICIADO, os);
    assertThat(saga.processar(iniciado)).isTrue();
    assertThat(saga.processar(iniciado)).isFalse();
    assertStatus(os, "EM_DIAGNOSTICO");

    adicionarPeca(token, os, idSku, 2);
    assertThat(evento(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO, os)).isTrue();
    assertStatus(os, "AGUARDANDO_APROVACAO");
    MensagemSaga gerar = ultimo(os);
    assertThat(gerar.tipo()).isEqualTo(TipoMensagemSaga.GERAR_ORCAMENTO);
    assertThat((List<?>) gerar.payload().get("itens")).hasSize(1);
    assertThat(gerar.correlationId()).isEqualTo("corr-" + os);

    assertThat(evento(TipoMensagemSaga.ORCAMENTO_APROVADO, os)).isTrue();
    assertStatus(os, "EM_EXECUCAO");
    assertThat(ultimo(os).tipo()).isEqualTo(TipoMensagemSaga.INICIAR_REPARO);
    assertSaldo(token, idSku, 0);

    assertThat(evento(TipoMensagemSaga.ORCAMENTO_APROVADO, os)).isFalse();
    assertThat(evento(TipoMensagemSaga.REPARO_CONCLUIDO, os)).isTrue();
    assertThat(ultimo(os).tipo()).isEqualTo(TipoMensagemSaga.SOLICITAR_PAGAMENTO);

    assertThat(evento(TipoMensagemSaga.PAGAMENTO_RECUSADO, os, "motivo", "Cartão negado")).isTrue();
    assertStatus(os, "AGUARDANDO_PAGAMENTO");
    assertThat(evento(TipoMensagemSaga.PAGAMENTO_CONFIRMADO, os, "pagamentoId", "MP-1")).isTrue();
    assertStatus(os, "PAGA");

    given()
        .header("Authorization", "Bearer " + tokenCliente(idCliente))
        .get("/consulta/ordens-servico/" + os + "/status")
        .then()
        .statusCode(200)
        .body("statusDescricao", equalTo("Paga"));
    given()
        .header("Authorization", "Bearer " + token)
        .get("/relatorios/tempo-medio-por-os")
        .then()
        .statusCode(200);
    given()
        .header("Authorization", "Bearer " + token)
        .get("/relatorios/os-por-status")
        .then()
        .statusCode(200);
  }

  @Test
  void estoqueInsuficienteNaAprovacaoCancelaOsECompensa() {
    String token = loginComoAdmin();
    Long idCliente = cadastrarCliente(token, "29528926444", "SAG2B22");
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
            {"placa":"SAG2B23","marca":"Fiat","modelo":"Uno","ano":2020,"idCliente":%d}
            """
                .formatted(idCliente))
        .post("veiculos")
        .then()
        .statusCode(201);
    Long idSku = cadastrarPecaComEstoque(token, "Correia", 1, "9002");
    String primeira = abrirOs(token, idCliente, "SAG2B22");
    String segunda = abrirOs(token, idCliente, "SAG2B23");
    adicionarPeca(token, primeira, idSku, 1);
    adicionarPeca(token, segunda, idSku, 1);
    evento(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO, primeira);
    evento(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO, segunda);

    evento(TipoMensagemSaga.ORCAMENTO_APROVADO, primeira);
    assertStatus(primeira, "EM_EXECUCAO");

    evento(TipoMensagemSaga.ORCAMENTO_APROVADO, segunda);
    assertStatus(segunda, "CANCELADA");
    assertThat(tipos(segunda))
        .endsWith(TipoMensagemSaga.CANCELAR_ORCAMENTO, TipoMensagemSaga.CANCELAR_EXECUCAO);
    assertSaldo(token, idSku, 0);
  }

  @Test
  void orcamentoRejeitadoComCancelamentoCompensaExecucao() {
    String token = loginComoAdmin();
    Long idCliente = cadastrarCliente(token, "73191675140", "SAG3C33");
    Long idSku = cadastrarPecaComEstoque(token, "Pastilha", 1, "9003");
    String os = abrirOs(token, idCliente, "SAG3C33");
    adicionarPeca(token, os, idSku, 1);
    evento(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO, os);

    evento(TipoMensagemSaga.ORCAMENTO_REJEITADO, os, "acao", "CANCELAR");
    assertStatus(os, "CANCELADA");
    MensagemSaga cancelar = ultimo(os);
    assertThat(cancelar.tipo()).isEqualTo(TipoMensagemSaga.CANCELAR_EXECUCAO);
    assertThat(cancelar.payload()).containsEntry("motivo", "Orçamento rejeitado pelo cliente");
    assertSaldo(token, idSku, 1);
  }

  @Test
  void comandoRecebidoNaFilaDeEventosEhIgnorado() {
    assertThat(evento(TipoMensagemSaga.INICIAR_REPARO, "OS-102026-999999")).isFalse();
  }

  private List<TipoMensagemSaga> tipos(String os) {
    return mensageriaMemoria.enviadasPara(os).stream().map(MensagemSaga::tipo).toList();
  }

  private MensagemSaga ultimo(String os) {
    List<MensagemSaga> enviadas = mensageriaMemoria.enviadasPara(os);
    return enviadas.get(enviadas.size() - 1);
  }

  private Long cadastrarCliente(String token, String documento, String placa) {
    Long idCliente =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                Map.of(
                    "nome",
                    "Cliente Saga",
                    "documento",
                    documento,
                    "email",
                    "saga" + documento + "@teste.com",
                    "telefone",
                    "11999999999"))
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
            """
            {"placa":"%s","marca":"Fiat","modelo":"Uno","ano":2020,"idCliente":%d}
            """
                .formatted(placa, idCliente))
        .post("veiculos")
        .then()
        .statusCode(201);
    return idCliente;
  }

  private Long cadastrarPecaComEstoque(String token, String nome, int quantidade, String nota) {
    Long idSku =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(Map.of("nome", nome, "precoVenda", 40.00))
            .post("pecas")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("idSku");
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
            {"numeroNota":"%s","serieNota":"1","cnpjFornecedor":"11222333000181",
             "dataEmissao":"2026-05-01","nomeFornecedor":"Fornecedor ACME",
             "itens":[{"idSku":%d,"quantidade":%d,"precoUnitario":20.00}]}
            """
                .formatted(nota, idSku, quantidade))
        .post("notas-fiscais-fornecedor")
        .then()
        .statusCode(201);
    return idSku;
  }

  private String abrirOs(String token, Long idCliente, String placa) {
    return given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(Map.of("idCliente", idCliente, "placa", placa, "descricaoProblema", "Revisão"))
        .post("ordens-servico")
        .then()
        .statusCode(201)
        .extract()
        .path("numero");
  }

  private void adicionarPeca(String token, String os, Long idSku, int quantidade) {
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(Map.of("idServicoSku", idSku, "quantidade", quantidade))
        .post("/ordens-servico/" + os + "/pecas")
        .then()
        .statusCode(200);
  }

  private void assertSaldo(String token, Long idSku, int saldo) {
    given()
        .header("Authorization", "Bearer " + token)
        .get("estoque")
        .then()
        .statusCode(200)
        .body("find { it.idSku == " + idSku + " }.quantidade", equalTo(saldo));
  }
}
