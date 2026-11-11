package br.com.oficina.adapter.controller;

import br.com.oficina.usecase.ClienteServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rota interna para o login por CPF do Auth Serverless. O Auth não acessa o banco do OS Service:
 * ele pergunta aqui se o CPF pertence a um cliente ativo.
 */
@RestController
@Tag(
    name = "05-Integração interna com o Auth Serverless",
    description = "Consulta usada pela Lambda de login; exige a chave técnica X-Api-Key.")
public class InternoClienteController {

  static final String HEADER_API_KEY = "X-Api-Key";

  private final ClienteServiceImpl clientes;
  private final byte[] apiKey;

  public InternoClienteController(
      ClienteServiceImpl clientes, @Value("${oficina.interno.api-key:}") String apiKey) {
    this.clientes = clientes;
    this.apiKey = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
  }

  @Schema(description = "CPF a autenticar.", example = "{\"cpf\":\"52998224725\"}")
  public record AutenticacaoClienteRequest(
      @Schema(description = "CPF com ou sem máscara.", example = "52998224725") String cpf) {}

  @Schema(description = "Cliente ativo encontrado.", example = "{\"idCliente\":1}")
  public record AutenticacaoClienteResponse(
      @Schema(description = "Id do cliente, usado como subject do JWT.", example = "1")
          Long idCliente) {}

  @PostMapping("/interno/clientes/autenticacao")
  @SecurityRequirements
  @Operation(
      summary = "Verifica se o CPF é de um cliente ativo (uso do Auth Serverless)",
      description =
          "Responde 404 para CPF inválido, inexistente ou inativo, sem dizer o motivo, para não"
              + " permitir enumeração de clientes.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Cliente ativo"),
    @ApiResponse(
        responseCode = "401",
        description = "Chave X-Api-Key ausente ou inválida",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "CPF não pertence a um cliente ativo",
        content = @Content)
  })
  public ResponseEntity<AutenticacaoClienteResponse> autenticar(
      @Parameter(description = "Chave técnica compartilhada com o Auth")
          @RequestHeader(name = HEADER_API_KEY, required = false)
          String chave,
      @RequestBody(required = false) AutenticacaoClienteRequest request) {
    if (!chaveValida(chave)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (request == null) {
      return ResponseEntity.notFound().build();
    }
    return clientes
        .idClienteAtivoPorCpf(request.cpf())
        .map(id -> ResponseEntity.ok(new AutenticacaoClienteResponse(id)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private boolean chaveValida(String chave) {
    return apiKey.length > 0
        && chave != null
        && MessageDigest.isEqual(apiKey, chave.getBytes(StandardCharsets.UTF_8));
  }
}
