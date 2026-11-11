package br.com.oficina.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

class EndpointsAdminTecnicoFase2IntegrationTest extends IntegrationTestBase {

  private Long criarCliente(String token, String doc) {
    return given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
{"nome":"Fase2 Cliente","documento":"%s","email":"f2@teste.com","telefone":"11955554444"}
"""
                .formatted(doc))
        .post("clientes")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("idCliente");
  }

  private void criarVeiculo(String token, Long idCliente, String placa) {
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
            {"placa":"%s","marca":"VW","modelo":"Gol","ano":2020,"idCliente":%d}
            """
                .formatted(placa, idCliente))
        .post("veiculos")
        .then()
        .statusCode(201);
  }

  @Test
  void abrirOsRecebidaCriaSemItensEmStatusRecebida() {
    String token = loginComoAdmin();
    Long idCliente = criarCliente(token, "12345600039");
    criarVeiculo(token, idCliente, "RCB0001");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
            {"idCliente":%d,"placa":"RCB0001","descricaoProblema":"Barulho"}
            """
                .formatted(idCliente))
        .post("/ordens-servico/recebida")
        .then()
        .statusCode(201)
        .body("status", equalTo("RECEBIDA"))
        .body("itens.size()", equalTo(0));
  }

  @Test
  void cancelarDiagnosticoMoveOsParaCancelada() {
    String token = loginComoAdmin();
    Long idCliente = criarCliente(token, "12345600110");
    criarVeiculo(token, idCliente, "DGN0001");

    Long idServico =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                """
                {"nome":"Diag","descricao":"Diag","precoBase":100.00}
                """)
            .post("servicos")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("idServico");

    String os =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                """
                {"idCliente":%d,"placa":"DGN0001","descricaoProblema":"Motor",
                 "itens":[{"idServicoSku":%d,"tipo":"SERVICO","quantidade":1}]}
                """
                    .formatted(idCliente, idServico))
            .post("ordens-servico")
            .then()
            .statusCode(201)
            .extract()
            .path("numero");

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body("{\"motivo\":\"Cliente desistiu\"}")
        .post("/ordens-servico/" + os + "/cancelar-diagnostico")
        .then()
        .statusCode(200)
        .body("status", equalTo("CANCELADA"));
  }

  @Test
  void alterarStatusContingenciaAplicaRegras() {
    String token = loginComoAdmin();
    Long idCliente = criarCliente(token, "12345600209");
    criarVeiculo(token, idCliente, "ALT0001");

    String os =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                """
                {"idCliente":%d,"placa":"ALT0001","descricaoProblema":"Freios"}
                """
                    .formatted(idCliente))
            .post("/ordens-servico/recebida")
            .then()
            .statusCode(201)
            .extract()
            .path("numero");

    // RECEBIDA -> EM_DIAGNOSTICO é permitido
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body("{\"status\":\"EM_DIAGNOSTICO\"}")
        .patch("/ordens-servico/" + os + "/status")
        .then()
        .statusCode(200)
        .body("status", equalTo("EM_DIAGNOSTICO"));

    // EM_EXECUCAO sem itens vinculados deve ser rejeitado (regra de negócio)
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body("{\"status\":\"EM_EXECUCAO\"}")
        .patch("/ordens-servico/" + os + "/status")
        .then()
        .statusCode(409);
  }
}
