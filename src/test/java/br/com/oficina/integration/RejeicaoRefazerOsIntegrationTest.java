package br.com.oficina.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import br.com.oficina.usecase.saga.TipoMensagemSaga;
import org.junit.jupiter.api.Test;

class RejeicaoRefazerOsIntegrationTest extends IntegrationTestBase {

  @Test
  void clienteRejeita_refazOrcamento_eAprovaNovo() {
    String token = loginComoAdmin();

    // Setup: cliente + veículo + serviço
    Long idCliente =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                """
{"nome":"Maria Rejeição","documento":"83574032714","email":"maria@teste.com","telefone":"11888888888"}
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
            {"placa":"DEF5678","marca":"VW","modelo":"Gol","ano":2019,"idCliente":%d}
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
                {"nome":"Alinhamento","descricao":"Alinhamento 3D","precoBase":120.00}
                """)
            .post("servicos")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("idServico");

    // Abrir OS
    String numeroOs =
        given()
            .header("Authorization", "Bearer " + token)
            .contentType("application/json")
            .body(
                """
                {"idCliente":%d,"placa":"DEF5678","descricaoProblema":"Pneu desgastado"}
                """
                    .formatted(idCliente))
            .post("ordens-servico")
            .then()
            .statusCode(201)
            .extract()
            .path("numero");

    // Adicionar serviço + enviar para aprovação
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
            {"idServicoSku":%d,"quantidade":1}
            """
                .formatted(idServico))
        .post("/ordens-servico/" + numeroOs + "/servicos")
        .then()
        .statusCode(200);

    evento(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO, numeroOs);
    assertStatus(numeroOs, "AGUARDANDO_APROVACAO");

    // Cliente rejeita e pede refazer → EM_DIAGNOSTICO
    evento(
        TipoMensagemSaga.ORCAMENTO_REJEITADO, numeroOs, "acao", "REFAZER", "motivo", "Muito caro");
    assertStatus(numeroOs, "EM_DIAGNOSTICO");

    // Técnico refaz orçamento e envia novamente
    given()
        .header("Authorization", "Bearer " + token)
        .contentType("application/json")
        .body(
            """
            {"idServicoSku":%d,"quantidade":1,"precoUnitario":80.00}
            """
                .formatted(idServico))
        .post("/ordens-servico/" + numeroOs + "/servicos")
        .then()
        .statusCode(200);

    evento(TipoMensagemSaga.DIAGNOSTICO_CONCLUIDO, numeroOs);
    assertStatus(numeroOs, "AGUARDANDO_APROVACAO");

    // Cliente aprova desta vez
    evento(TipoMensagemSaga.ORCAMENTO_APROVADO, numeroOs);
    assertStatus(numeroOs, "EM_EXECUCAO");
  }
}
