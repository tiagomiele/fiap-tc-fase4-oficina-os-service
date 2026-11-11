package br.com.oficina.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

class NotaFiscalEstoqueIntegrationTest extends IntegrationTestBase {

  @Test
  void registrarNF_creditaEstoque_e_geraContaAPagar() {
    String token = loginComoAdmin();

    // Cadastrar peça
    Long idSku =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                """
                {"nome":"Filtro de óleo","precoVenda":35.00}
                """)
            .post("pecas")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("idSku");

    // Registrar NF do fornecedor
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
            {
              "numeroNota":"12345",
              "serieNota":"1",
              "cnpjFornecedor":"11222333000181",
              "dataEmissao":"2026-05-01",
              "nomeFornecedor":"Fornecedor ACME",
              "itens":[{"idSku":%d,"quantidade":10,"precoUnitario":20.00}]
            }
            """
                .formatted(idSku))
        .post("notas-fiscais-fornecedor")
        .then()
        .statusCode(201);

    // Verificar estoque
    given()
        .header("Authorization", "Bearer " + token)
        .get("estoque")
        .then()
        .statusCode(200)
        .body("find { it.idSku == " + idSku + " }.quantidade", equalTo(10));

    // Verificar conta a pagar
    given()
        .header("Authorization", "Bearer " + token)
        .get("contas-a-pagar")
        .then()
        .statusCode(200)
        .body("size()", greaterThanOrEqualTo(1));
  }
}
