package br.com.oficina.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  @Test
  void documentaAutenticacaoDoClienteNoApiGatewayConfigurado() {
    OpenAPI openApi = new OpenApiConfig("https://auth.example.com/").oficinaOpenAPI();

    var operation = openApi.getPaths().get("/auth/cpf").getPost();

    assertThat(operation.getServers())
        .singleElement()
        .extracting(server -> server.getUrl())
        .isEqualTo("https://auth.example.com");
    assertThat(operation.getSecurity()).isEmpty();
    var requestMediaType = operation.getRequestBody().getContent().get("application/json");
    var successMediaType = operation.getResponses().get("200").getContent().get("application/json");

    assertThat(requestMediaType.getSchema().get$ref())
        .isEqualTo("#/components/schemas/CpfAuthenticationRequest");
    assertThat(requestMediaType.getExample()).isEqualTo(Map.of("cpf", "52998224725"));
    assertThat(successMediaType.getSchema().get$ref())
        .isEqualTo("#/components/schemas/CpfAuthenticationResponse");
    assertThat(operation.getResponses()).containsKeys("200", "400", "401", "500");
    assertThat(openApi.getComponents().getSchemas())
        .containsKeys("CpfAuthenticationRequest", "CpfAuthenticationResponse");
  }

  @Test
  void usaEnderecoExplicitoQuandoApiGatewayNaoEstaConfigurado() {
    OpenAPI openApi = new OpenApiConfig("").oficinaOpenAPI();

    assertThat(openApi.getPaths().get("/auth/cpf").getPost().getServers())
        .singleElement()
        .extracting(server -> server.getUrl())
        .isEqualTo("https://configure-auth-base-url.invalid");
  }

  @Test
  void identificaFaseQuatroSemDivulgarSenhaPadrao() {
    OpenAPI openApi = new OpenApiConfig("https://auth.example.com").oficinaOpenAPI();

    assertThat(openApi.getInfo().getTitle()).contains("Fase 4");
    assertThat(openApi.getInfo().getVersion()).isEqualTo("4.0.0");
    assertThat(openApi.getInfo().getDescription()).doesNotContain("admin123");
  }
}
