package br.com.oficina.adapter.controller;

import br.com.oficina.adapter.dto.OrdemServicoStatusResponse;
import br.com.oficina.adapter.security.AuthenticatedPrincipal;
import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.model.HistoricoStatusOrdemServico;
import br.com.oficina.usecase.OrdemServicoServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(
    name = "04-Perfil de acesso do Cliente na Oficina",
    description =
        "Endpoints do cliente autenticado por JWT serverless. Cada operação valida a propriedade"
            + " da Ordem de Serviço.")
public class ClienteOficinaController {

  private static final String DESC_NUMERO_OS =
      "Número da Ordem de Serviço no formato OS-MMAAAA-NNNNNN.";
  private static final String EXAMPLE_NUMERO_OS = "OS-042026-000001";

  private final OrdemServicoServiceImpl service;

  public ClienteOficinaController(OrdemServicoServiceImpl service) {
    this.service = service;
  }

  public record HistoricoStatusResponse(
      StatusOrdemServico status,
      Instant entradaEm,
      Instant saidaEm,
      Long duracaoMilisegundos,
      String correlationId) {

    static HistoricoStatusResponse de(HistoricoStatusOrdemServico historico) {
      return new HistoricoStatusResponse(
          historico.status(),
          historico.entradaEm(),
          historico.saidaEm(),
          historico.duracaoMilisegundos(),
          historico.correlationId());
    }
  }

  @Operation(summary = "04.01 - Consultar status da própria OS")
  @GetMapping("/consulta/ordens-servico/{numeroOs}/status")
  public OrdemServicoStatusResponse consultarStatus(
      @Parameter(description = DESC_NUMERO_OS, example = EXAMPLE_NUMERO_OS) @PathVariable
          String numeroOs,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return OrdemServicoStatusResponse.de(service.consultarDoCliente(numeroOs, clientId(principal)));
  }

  @Operation(summary = "04.02 - Consultar histórico de status da própria OS")
  @GetMapping("/ordens-servico/{numeroOs}/historico")
  public List<HistoricoStatusResponse> consultarHistorico(
      @Parameter(description = DESC_NUMERO_OS, example = EXAMPLE_NUMERO_OS)
          @PathVariable("numeroOs")
          String numero,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return service.historicoDoCliente(numero, clientId(principal)).stream()
        .map(HistoricoStatusResponse::de)
        .toList();
  }

  private static Long clientId(AuthenticatedPrincipal principal) {
    if (principal == null || !principal.isClient()) {
      throw new BusinessException("ACESSO_NEGADO", "Acesso de cliente obrigatório");
    }
    return principal.clientId();
  }
}
