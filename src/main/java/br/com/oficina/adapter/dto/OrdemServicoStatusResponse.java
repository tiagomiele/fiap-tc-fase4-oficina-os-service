package br.com.oficina.adapter.dto;

import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.model.OrdemServico;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Retorno da consulta pública de status de OS. Exposto em {@code GET
 * /consulta/ordens-servico/{numeroOs}/status}.
 */
@Schema(
    description = "Status resumido de uma Ordem de Serviço para consulta pública pelo cliente.",
    example =
        "{\"numeroOs\":\"OS-042026-000001\",\"status\":\"EM_EXECUCAO\","
            + "\"statusDescricao\":\"Em execução\"}")
public record OrdemServicoStatusResponse(
    @Schema(description = "Número da OS no formato OS-MMAAAA-NNNNNN.", example = "OS-042026-000001")
        String numeroOs,
    @Schema(description = "Status atual da OS (enum).", example = "EM_EXECUCAO") String status,
    @Schema(description = "Descrição legível do status em pt-BR.", example = "Em execução")
        String statusDescricao) {

  public static OrdemServicoStatusResponse de(OrdemServico os) {
    return new OrdemServicoStatusResponse(
        os.getNumero().valor(), os.getStatus().name(), descricaoDe(os.getStatus()));
  }

  private static String descricaoDe(StatusOrdemServico s) {
    return switch (s) {
      case RECEBIDA -> "Recebida";
      case EM_DIAGNOSTICO -> "Em diagnóstico";
      case AGUARDANDO_APROVACAO -> "Aguardando aprovação do orçamento";
      case EM_EXECUCAO -> "Em execução";
      case AGUARDANDO_PAGAMENTO -> "Aguardando pagamento";
      case PAGA -> "Paga";
      case ENTREGUE -> "Entregue";
      case CANCELADA -> "Cancelada";
    };
  }
}
