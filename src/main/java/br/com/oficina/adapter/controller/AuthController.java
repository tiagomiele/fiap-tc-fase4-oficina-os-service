package br.com.oficina.adapter.controller;

import br.com.oficina.domain.enums.Papel;
import br.com.oficina.domain.model.User;
import br.com.oficina.usecase.AuthServiceImpl;
import br.com.oficina.usecase.UserServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 01 - AuthController — "Autenticação e Logins para Aplicação".
 *
 * <p>Concentra:
 *
 * <ul>
 *   <li>{@code POST /auth/login} (público)
 *   <li>{@code POST /usuarios} (cadastro de funcionários/técnicos — exige role {@code
 *       FUNCIONARIO_DA_OFICINA})
 * </ul>
 */
@RestController
@Tag(
    name = "01-Autenticação e Logins para Aplicação",
    description =
        "Emissão de JWT para clientes pelo Auth Serverless, login de funcionários/técnicos e"
            + " cadastro de usuários da oficina.")
public class AuthController {

  private final AuthServiceImpl loginService;
  private final UserServiceImpl userService;

  public AuthController(AuthServiceImpl loginService, UserServiceImpl userService) {
    this.loginService = loginService;
    this.userService = userService;
  }

  // ===== Schemas =====================================================================

  @Schema(
      description =
          "Credenciais de login. O campo `validadeMinutos` é opcional (1–1440 min, default 60).",
      example =
          "{\"email\":\"admin@oficina.local\",\"senha\":\"informe-a-senha-configurada\","
              + "\"validadeMinutos\":120}")
  public record LoginRequest(
      @Schema(
              description = "E-mail do usuário cadastrado.",
              example = "admin@oficina.local",
              requiredMode = Schema.RequiredMode.REQUIRED)
          @Email
          @NotBlank
          String email,
      @Schema(
              description = "Senha em texto puro (o hash é feito no servidor com BCrypt).",
              example = "informe-a-senha-configurada",
              requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank
          String senha,
      @Schema(
              description =
                  "Validade do token em minutos. Opcional. Default = 60. Limites: 1 a 1440 (24h).",
              example = "120",
              nullable = true,
              minimum = "1",
              maximum = "1440")
          @Min(value = 1, message = "validadeMinutos deve ser >= 1")
          @Max(value = 1440, message = "validadeMinutos deve ser <= 1440 (24h)")
          Integer validadeMinutos) {}

  @Schema(
      description = "Retorno do login contendo o JWT e metadados.",
      example =
          "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9...\",\"expiresIn\":7200,"
              + "\"papel\":\"FUNCIONARIO_DA_OFICINA\",\"email\":\"admin@oficina.local\"}")
  public record LoginResponse(
      @Schema(
              description =
                  "JWT de acesso (Bearer). Use no header"
                      + " `Authorization: Bearer <accessToken>` nos demais endpoints.",
              example = "eyJhbGciOiJIUzI1NiJ9...")
          String accessToken,
      @Schema(
              description =
                  "Validade do token em segundos (corresponde ao `validadeMinutos * 60`).",
              example = "7200")
          long expiresIn,
      @Schema(
              description = "Papel do usuário autenticado.",
              example = "FUNCIONARIO_DA_OFICINA",
              allowableValues = {"FUNCIONARIO_DA_OFICINA", "TECNICO_DA_OFICINA"})
          String papel,
      @Schema(description = "E-mail do usuário autenticado.", example = "admin@oficina.local")
          String email) {}

  @Schema(
      description = "Dados para cadastro de um novo usuário administrativo (funcionário/técnico).",
      example =
          "{\"email\":\"tecnico@oficina.local\",\"senha\":\"tecnico123\","
              + "\"papel\":\"TECNICO_DA_OFICINA\"}")
  public record CreateUserRequest(
      @Schema(description = "E-mail único do usuário.", example = "tecnico@oficina.local")
          @Email
          @NotBlank
          String email,
      @Schema(description = "Senha em texto puro (será criptografada).", example = "tecnico123")
          @NotBlank
          String senha,
      @Schema(
              description = "Papel do usuário no sistema.",
              example = "TECNICO_DA_OFICINA",
              allowableValues = {"FUNCIONARIO_DA_OFICINA", "TECNICO_DA_OFICINA"})
          @NotNull
          Papel papel) {}

  @Schema(
      description = "Dados de retorno do usuário cadastrado.",
      example =
          "{\"id\":\"3a7c0e0e-1f3a-4b40-9b6e-1f2a3b4c5d6e\","
              + "\"email\":\"tecnico@oficina.local\","
              + "\"papel\":\"TECNICO_DA_OFICINA\",\"ativo\":true}")
  public record UserResponse(
      @Schema(
              description = "Identificador único do usuário (UUID).",
              example = "3a7c0e0e-1f3a-4b40-9b6e-1f2a3b4c5d6e")
          String id,
      @Schema(description = "E-mail do usuário.", example = "tecnico@oficina.local") String email,
      @Schema(description = "Papel do usuário.", example = "TECNICO_DA_OFICINA") Papel papel,
      @Schema(description = "Se o usuário está ativo.", example = "true") boolean ativo) {}

  // ===== Endpoints ===================================================================

  /** 01 - Autenticação. */
  @Operation(
      summary = "01.02 - Funcionário/Técnico — login e emissão de JWT",
      description =
          "Autentica o usuário por e-mail/senha e retorna um JWT de acesso. Não há refresh"
              + " token — controle a duração com o campo opcional `validadeMinutos`"
              + " (1–1440 min, default 60).")
  @SecurityRequirements
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Token emitido com sucesso.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = LoginResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validação falhou (VALIDACAO)."),
    @ApiResponse(
        responseCode = "401",
        description = "Credenciais inválidas (CREDENCIAIS_INVALIDAS).")
  })
  @PostMapping("/auth/login")
  public LoginResponse login(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              description = "Credenciais + validade opcional do token",
              content =
                  @Content(
                      examples = {
                        @ExampleObject(
                            name = "default",
                            summary = "Login de funcionário (token de 60 min)",
                            value =
                                "{\"email\":\"admin@oficina.local\","
                                    + "\"senha\":\"informe-a-senha-configurada\"}"),
                        @ExampleObject(
                            name = "comValidade",
                            summary = "Login com validade customizada (120 min)",
                            value =
                                "{\"email\":\"tecnico@oficina.local\","
                                    + "\"senha\":\"informe-a-senha-configurada\","
                                    + "\"validadeMinutos\":120}")
                      }))
          @Valid
          @RequestBody
          LoginRequest body) {
    var r = loginService.executar(body.email(), body.senha(), body.validadeMinutos());
    return new LoginResponse(r.accessToken(), r.expiresIn(), r.papel(), r.email());
  }

  /** 02 - Usuários Administrativos / criar. */
  @Operation(
      summary = "01.03 - Usuários Administrativos — cadastrar novo usuário",
      description =
          "Cadastra um funcionário ou técnico da oficina. Exige token JWT de"
              + " `FUNCIONARIO_DA_OFICINA`.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Usuário criado.",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserResponse.class))),
    @ApiResponse(responseCode = "400", description = "Validação falhou (VALIDACAO)."),
    @ApiResponse(responseCode = "401", description = "Não autenticado."),
    @ApiResponse(responseCode = "403", description = "Sem permissão (ACESSO_NEGADO)."),
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado (USUARIO_DUPLICADO).")
  })
  @PostMapping("/usuarios")
  @PreAuthorize("hasRole('FUNCIONARIO_DA_OFICINA')")
  public ResponseEntity<UserResponse> criarUsuario(@Valid @RequestBody CreateUserRequest req) {
    User u = userService.executar(req.email(), req.senha(), req.papel());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new UserResponse(u.getId().toString(), u.getEmail(), u.getPapel(), u.isAtivo()));
  }
}
