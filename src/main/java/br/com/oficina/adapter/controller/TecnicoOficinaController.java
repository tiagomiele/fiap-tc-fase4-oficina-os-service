package br.com.oficina.adapter.controller;

import br.com.oficina.adapter.dto.OrdemServicoResponse;
import br.com.oficina.usecase.OrdemServicoServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 03 - TecnicoOficinaController — "Perfil Técnico Assistência Técnica da Oficina".
 *
 * <p>Endpoints do dia a dia da assistência técnica: montar orçamento (serviços + peças),
 * consultar/listar, enviar para aprovação, concluir reparo e entregar veículo. A abertura da OS
 * propriamente dita ({@code POST /ordens-servico}) é um ato administrativo e fica em {@link
 * AdministrativoOficinaController}.
 *
 * <p>Por hierarquia de roles, {@code FUNCIONARIO_DA_OFICINA} também pode chamar todos esses
 * endpoints — esses endpoints requerem no mínimo {@code TECNICO_DA_OFICINA}.
 */
@RestController
@RequestMapping("/ordens-servico")
@PreAuthorize("hasRole('TECNICO_DA_OFICINA')")
@Tag(
    name = "03-Perfil Técnico Assistência Técnica da Oficina",
    description =
        "Operações da OS executadas pela assistência técnica: montar orçamento (serviços +"
            + " peças), listar, consultar, enviar para aprovação, concluir reparo e entregar.")
public class TecnicoOficinaController {

  private static final String DESC_NUMERO_OS =
      "Número da Ordem de Serviço no formato OS-MMAAAA-NNNNNN.";
  private static final String EXAMPLE_NUMERO_OS = "OS-042026-000001";

  private final OrdemServicoServiceImpl service;

  public TecnicoOficinaController(OrdemServicoServiceImpl service) {
    this.service = service;
  }

  // ===== Schemas =====================================================================

  @Schema(
      description = "Dados para adicionar serviço ou peça ao orçamento atual.",
      example = "{\"idServicoSku\":1,\"quantidade\":1,\"precoUnitario\":150.00}")
  public record AdicionarItemRequest(
      @Schema(
              description =
                  "ID do serviço (tipo SERVICO) ou SKU da peça (tipo PECA). Validado em runtime.",
              example = "1")
          @NotNull
          Long idServicoSku,
      @Schema(description = "Quantidade (> 0).", example = "1") @Positive int quantidade,
      @Schema(
              description = "Preço unitário (>= 0). Se omitido, usa o preço do catálogo.",
              example = "150.00",
              nullable = true)
          BigDecimal precoUnitario) {}

  @Schema(
      description = "Motivo (opcional) do cancelamento da OS em diagnóstico.",
      example = "{\"motivo\":\"Cliente desistiu do reparo\"}")
  public record CancelarDiagnosticoRequest(
      @Schema(description = "Motivo do cancelamento.", example = "Cliente desistiu do reparo")
          String motivo) {}

  // ===== Endpoints ===================================================================

  @Operation(summary = "03.01 - Adicionar serviço ao orçamento atual")
  @PostMapping("/{numeroOs}/servicos")
  public OrdemServicoResponse adicionarServico(
      @Parameter(name = "numeroOs", description = DESC_NUMERO_OS, example = EXAMPLE_NUMERO_OS)
          @PathVariable("numeroOs")
          String numero,
      @Valid @RequestBody AdicionarItemRequest req) {
    return OrdemServicoResponse.de(
        service.adicionarServico(
            numero, req.idServicoSku(), req.quantidade(), req.precoUnitario()));
  }

  @Operation(
      summary = "03.02 - Adicionar peça ao orçamento atual",
      description = "Consome a peça do estoque imediatamente (estorno ocorre em rejeição).")
  @PostMapping("/{numeroOs}/pecas")
  public OrdemServicoResponse adicionarPeca(
      @Parameter(name = "numeroOs", description = DESC_NUMERO_OS, example = EXAMPLE_NUMERO_OS)
          @PathVariable("numeroOs")
          String numero,
      @Valid @RequestBody AdicionarItemRequest req) {
    return OrdemServicoResponse.de(
        service.adicionarPeca(numero, req.idServicoSku(), req.quantidade(), req.precoUnitario()));
  }

  @Operation(summary = "03.03 - Listar todas as OS")
  @GetMapping
  public List<OrdemServicoResponse> listar() {
    return service.listar().stream().map(OrdemServicoResponse::de).toList();
  }

  @Operation(summary = "03.04 - Buscar OS pelo número")
  @GetMapping("/{numeroOs}")
  public OrdemServicoResponse buscar(
      @Parameter(name = "numeroOs", description = DESC_NUMERO_OS, example = EXAMPLE_NUMERO_OS)
          @PathVariable("numeroOs")
          String numero) {
    return OrdemServicoResponse.de(service.consultar(numero));
  }

  @Operation(
      summary = "03.05 - Entregar veículo ao cliente",
      description =
          "Aceita status PAGA ou CANCELADA. Em CANCELADA apenas registra a entrega sem ação"
              + " financeira.")
  @PostMapping("/{numeroOs}/entregar")
  public OrdemServicoResponse entregar(
      @Parameter(name = "numeroOs", description = DESC_NUMERO_OS, example = EXAMPLE_NUMERO_OS)
          @PathVariable("numeroOs")
          String numero) {
    return OrdemServicoResponse.de(service.entregar(numero));
  }

  @Operation(
      summary = "03.06 - Cancelar OS em diagnóstico",
      description =
          "Cancela uma OS que esteja em EM_DIAGNOSTICO: cancela todos os orçamentos vinculados,"
              + " devolve as peças reservadas ao estoque e move a OS para CANCELADA.")
  @PostMapping("/{numeroOs}/cancelar-diagnostico")
  public OrdemServicoResponse cancelarDiagnostico(
      @Parameter(name = "numeroOs", description = DESC_NUMERO_OS, example = EXAMPLE_NUMERO_OS)
          @PathVariable("numeroOs")
          String numero,
      @RequestBody(required = false) CancelarDiagnosticoRequest req) {
    String motivo = req == null ? null : req.motivo();
    return OrdemServicoResponse.de(service.cancelarEmDiagnostico(numero, motivo));
  }
}
