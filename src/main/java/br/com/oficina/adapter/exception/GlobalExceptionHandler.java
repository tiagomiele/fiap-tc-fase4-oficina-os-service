package br.com.oficina.adapter.exception;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.usecase.gateway.ObservabilidadeGateway;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final Pattern ORDEM_SERVICO_PATH = Pattern.compile("/ordens-servico/([^/?]+)");

  private final ObservabilidadeGateway observabilidade;

  public GlobalExceptionHandler(ObservabilidadeGateway observabilidade) {
    this.observabilidade = observabilidade;
  }

  private static final Set<String> CONFLICT_CODES =
      Set.of(
          "CLIENTE_DUPLICADO",
          "VEICULO_DUPLICADO",
          "SERVICO_DUPLICADO",
          "PECA_DUPLICADA",
          "NF_DUPLICADA",
          "USUARIO_DUPLICADO",
          "ORDEM_SERVICO_STATUS_INVALIDO",
          "ORCAMENTO_NAO_DISPONIVEL",
          "CLIENTE_COM_OS_ATIVA",
          "VEICULO_COM_OS_ATIVA",
          "SERVICO_COM_OS_ATIVA",
          "PECA_COM_OS_ATIVA",
          "ESTOQUE_INSUFICIENTE");

  private static final Set<String> NOT_FOUND_CODES =
      Set.of(
          "CLIENTE_NAO_CADASTRADO",
          "VEICULO_NAO_CADASTRADO",
          "SERVICO_NAO_CADASTRADO",
          "PECA_NAO_CADASTRADA",
          "OS_NAO_ENCONTRADA",
          "NF_NAO_ENCONTRADA",
          "USUARIO_NAO_ENCONTRADO",
          "ITEM_ORCAMENTO_NAO_ENCONTRADO");

  private static final Set<String> UNAUTH_CODES = Set.of("CREDENCIAIS_INVALIDAS", "TOKEN_INVALIDO");

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiError> handleDominio(BusinessException ex, HttpServletRequest req) {
    HttpStatus status;
    if (NOT_FOUND_CODES.contains(ex.getCodigo())) {
      status = HttpStatus.NOT_FOUND;
    } else if (CONFLICT_CODES.contains(ex.getCodigo())) {
      status = HttpStatus.CONFLICT;
    } else if (UNAUTH_CODES.contains(ex.getCodigo())) {
      status = HttpStatus.UNAUTHORIZED;
    } else {
      status = HttpStatus.UNPROCESSABLE_ENTITY;
    }
    registrarFalhaOrdemServico(req, ex.getCodigo());
    return build(status, ex.getCodigo(), ex.getMessage(), req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    List<String> detalhes =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .toList();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            new ApiError(
                400,
                "VALIDACAO",
                "Requisição inválida",
                req.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY),
                detalhes));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleJson(
      HttpMessageNotReadableException ex, HttpServletRequest req) {
    return build(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "JSON inválido", req);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String mensagem;
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
      mensagem =
          "Acesso negado: token JWT ausente ou inválido. Faça login em POST /auth/login para"
              + " obter um token válido.";
    } else {
      String papelAtual =
          auth.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .filter(a -> a.startsWith("ROLE_"))
              .map(a -> a.substring(5))
              .findFirst()
              .orElse("DESCONHECIDO");
      mensagem =
          "Acesso negado: seu perfil ("
              + papelAtual
              + ") não tem permissão para este endpoint. É necessário FUNCIONARIO_DA_OFICINA"
              + " (ou TECNICO_DA_OFICINA, conforme o endpoint).";
    }
    return build(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", mensagem, req);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiError> handleBadCreds(
      BadCredentialsException ex, HttpServletRequest req) {
    return build(HttpStatus.UNAUTHORIZED, "CREDENCIAIS_INVALIDAS", "Credenciais inválidas", req);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ApiError> handle404(NoHandlerFoundException ex, HttpServletRequest req) {
    return build(HttpStatus.NOT_FOUND, "NAO_ENCONTRADO", "Recurso não encontrado", req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
    registrarFalhaOrdemServico(req, "ERRO_INTERNO");
    log.error("Erro não tratado: tipoErro={}", ex.getClass().getSimpleName());
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Erro interno", req);
  }

  private void registrarFalhaOrdemServico(HttpServletRequest req, String codigoErro) {
    try {
      Matcher matcher = ORDEM_SERVICO_PATH.matcher(req.getRequestURI());
      if (matcher.find()) {
        observabilidade.ordemServicoProcessamentoFalhou(
            matcher.group(1), req.getMethod(), codigoErro);
      }
    } catch (RuntimeException exception) {
      log.warn("Falha ao registrar erro da ordem de serviço na observabilidade", exception);
    }
  }

  private ResponseEntity<ApiError> build(
      HttpStatus status, String codigo, String mensagem, HttpServletRequest req) {
    return ResponseEntity.status(status)
        .body(
            new ApiError(
                status.value(),
                codigo,
                mensagem,
                req.getRequestURI(),
                MDC.get(RequestIdFilter.MDC_KEY)));
  }
}
