package br.com.oficina.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String AUTH_TAG = "01-Autenticação e Logins para Aplicação";
  private static final String UNCONFIGURED_AUTH_URL = "https://configure-auth-base-url.invalid";

  private final String authBaseUrl;

  public OpenApiConfig(@Value("${oficina.security.serverless-auth.base-url:}") String authBaseUrl) {
    this.authBaseUrl = normalizeBaseUrl(authBaseUrl);
  }

  @Bean
  public OpenAPI oficinaOpenAPI() {
    Components components =
        new Components()
            .addSecuritySchemes(
                "bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT"))
            .addSchemas(
                "CpfAuthenticationRequest",
                new ObjectSchema()
                    .required(List.of("cpf"))
                    .addProperty(
                        "cpf",
                        new StringSchema()
                            .description("CPF do cliente, com ou sem máscara.")
                            .minLength(11)
                            .maxLength(14)))
            .addSchemas(
                "CpfAuthenticationResponse",
                new ObjectSchema()
                    .required(List.of("accessToken", "tokenType", "expiresIn"))
                    .addProperty(
                        "accessToken",
                        new StringSchema().description("JWT RS256 para uso como Bearer token."))
                    .addProperty("tokenType", new StringSchema().example("Bearer"))
                    .addProperty(
                        "expiresIn",
                        new IntegerSchema()
                            .format("int64")
                            .description("Validade do token em segundos.")));

    Operation cpfAuthentication =
        new Operation()
            .operationId("authenticateClientByCpf")
            .summary("01.01 - Cliente — autenticação por CPF e emissão de JWT")
            .description(
                "Autentica um cliente ativo pela API Gateway serverless. O perfil CLIENTE é"
                    + " determinado pelo cadastro; não é informado no request.")
            .tags(List.of(AUTH_TAG))
            .servers(
                List.of(
                    new Server()
                        .url(authBaseUrl)
                        .description("API Gateway do Auth Serverless neste ambiente")))
            .security(List.of())
            .requestBody(
                new RequestBody()
                    .required(true)
                    .content(
                        new Content()
                            .addMediaType(
                                "application/json",
                                new MediaType()
                                    .schema(
                                        new Schema<>()
                                            .$ref(
                                                "#/components/schemas/"
                                                    + "CpfAuthenticationRequest"))
                                    .example(Map.of("cpf", "52998224725")))))
            .responses(
                new ApiResponses()
                    .addApiResponse(
                        "200",
                        new ApiResponse()
                            .description("Token de cliente emitido com sucesso.")
                            .content(
                                new Content()
                                    .addMediaType(
                                        "application/json",
                                        new MediaType()
                                            .schema(
                                                new Schema<>()
                                                    .$ref(
                                                        "#/components/schemas/"
                                                            + "CpfAuthenticationResponse")))))
                    .addApiResponse("400", new ApiResponse().description("Requisição inválida."))
                    .addApiResponse(
                        "401",
                        new ApiResponse()
                            .description("CPF inválido, inexistente ou cliente inativo."))
                    .addApiResponse(
                        "500", new ApiResponse().description("Falha interna no Auth Serverless.")));

    return new OpenAPI()
        .info(
            new Info()
                .title("Tech Challenge FIAP - Fase 4 - Oficina Mecânica — API")
                .description(
                    "API da oficina mecânica. Clientes autenticam por CPF no Auth Serverless;"
                        + " funcionários e técnicos autenticam por e-mail e senha no OS Service.")
                .version("4.0.0"))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(components)
        .path("/auth/cpf", new PathItem().post(cpfAuthentication));
  }

  private static String normalizeBaseUrl(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return UNCONFIGURED_AUTH_URL;
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
