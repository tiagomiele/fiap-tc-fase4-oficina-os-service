package br.com.oficina.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import br.com.oficina.usecase.saga.TipoMensagemSaga;
import org.junit.jupiter.api.Test;

class ListagemAtivasIntegrationTest extends IntegrationTestBase {

  @Test
  void listagemAtivasOrdenadaPorPrioridade() {
    String token = loginComoAdmin();

    // Cadastrar cliente + veículo + serviço
    Long idCliente =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                """
{"nome":"Pedro Lista","documento":"71924748436","email":"pedro@teste.com","telefone":"11777777777"}
""")
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
            {"placa":"GHI9012","marca":"Honda","modelo":"Civic","ano":2022,"idCliente":%d}
            """
                .formatted(idCliente))
        .post("veiculos")
        .then()
        .statusCode(201);

    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
            {"placa":"JKL3456","marca":"Toyota","modelo":"Corolla","ano":2021,"idCliente":%d}
            """
                .formatted(idCliente))
        .post("veiculos")
        .then()
        .statusCode(201);

    Long idServico =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                """
                {"nome":"Revisão","descricao":"Revisão completa","precoBase":300.00}
                """)
            .post("servicos")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("idServico");

    // OS1 — ficará RECEBIDA (prioridade 4)
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
            {"idCliente":%d,"placa":"GHI9012","descricaoProblema":"Check engine"}
            """
                .formatted(idCliente))
        .post("ordens-servico")
        .then()
        .statusCode(201);

    // OS2 — será levada a EM_EXECUCAO (prioridade 1) via abertura unificada
    String os2 =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                """
                {"idCliente":%d,"placa":"JKL3456","descricaoProblema":"AC não funciona",
                 "itens":[{"idServicoSku":%d,"tipo":"SERVICO","quantidade":1}]}
                """
                    .formatted(idCliente, idServico))
            .post("ordens-servico")
            .then()
            .statusCode(201)
            .extract()
            .path("numero");

    // Enviar OS2 para aprovação e aprovar
    evento(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO, os2);
    evento(TipoMensagemSaga.ORCAMENTO_APROVADO, os2);

    // GET /relatorios/os-por-status — OS2 (EM_EXECUCAO) deve vir primeiro
    given()
        .header("Authorization", "Bearer " + token)
        .get("/relatorios/os-por-status")
        .then()
        .statusCode(200)
        .body("size()", greaterThanOrEqualTo(2))
        .body("[0].status", equalTo("EM_EXECUCAO"));
  }
}
