package br.com.oficina.adapter.dto;

import br.com.oficina.usecase.gateway.RelatorioGateway;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
    description =
        "Relatório de tempo médio (em horas) de execução das Ordens de Serviço encerradas."
            + " Cálculo: média de (`fim_execucao` - `inicio_execucao`) sobre todas as OS que"
            + " possuem ambos os timestamps preenchidos. `inicio_execucao` é gravado na"
            + " primeira transição para EM_EXECUCAO (aprovação do orçamento); `fim_execucao`"
            + " na primeira transição para AGUARDANDO_PAGAMENTO (fim do reparo). OS sem ambos"
            + " os timestamps são ignoradas.",
    example =
        "{\"tempoMedioHoras\":2.75,\"totalOrdens\":2,"
            + "\"ordens\":["
            + "{\"numeroOs\":\"OS-052026-000001\",\"inicioExecucao\":\"2026-05-11T09:00:00Z\","
            + "\"fimExecucao\":\"2026-05-11T11:30:00Z\",\"duracaoHoras\":2.5},"
            + "{\"numeroOs\":\"OS-052026-000002\",\"inicioExecucao\":\"2026-05-12T13:00:00Z\","
            + "\"fimExecucao\":\"2026-05-12T16:00:00Z\",\"duracaoHoras\":3.0}]}")
public record TempoMedioPorOsResponse(
    @Schema(
            description =
                "Tempo médio em horas das OS encerradas (0.0 quando não há OS encerradas).",
            example = "2.75")
        Double tempoMedioHoras,
    @Schema(description = "Total de OS consideradas no cálculo.", example = "2") int totalOrdens,
    @Schema(
            description =
                "Lista das OS encerradas que entraram no cálculo, ordenadas por fim_execucao.")
        List<OrdemExecutadaResponse> ordens) {

  @Schema(
      description = "Duração efetiva de execução de uma OS encerrada.",
      example =
          "{\"numeroOs\":\"OS-052026-000001\",\"inicioExecucao\":\"2026-05-11T09:00:00Z\","
              + "\"fimExecucao\":\"2026-05-11T11:30:00Z\",\"duracaoHoras\":2.5}")
  public record OrdemExecutadaResponse(
      @Schema(
              description = "Número da OS no formato OS-MMAAAA-NNNNNN.",
              example = "OS-052026-000001")
          String numeroOs,
      @Schema(
              description = "Instante da 1ª transição para EM_EXECUCAO.",
              example = "2026-05-11T09:00:00Z")
          Instant inicioExecucao,
      @Schema(
              description = "Instante da 1ª transição para AGUARDANDO_PAGAMENTO.",
              example = "2026-05-11T11:30:00Z")
          Instant fimExecucao,
      @Schema(description = "Duração da execução em horas decimais.", example = "2.5")
          Double duracaoHoras) {}

  public static TempoMedioPorOsResponse de(RelatorioGateway.RelatorioResult r) {
    List<OrdemExecutadaResponse> ordens =
        r.ordens().stream()
            .map(
                o ->
                    new OrdemExecutadaResponse(
                        o.numeroOs(), o.inicioExecucao(), o.fimExecucao(), o.duracaoHoras()))
            .toList();
    return new TempoMedioPorOsResponse(r.tempoMedioHoras(), r.totalOrdens(), ordens);
  }
}
