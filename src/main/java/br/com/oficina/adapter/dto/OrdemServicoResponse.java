package br.com.oficina.adapter.dto;

import br.com.oficina.domain.model.ItemOrcamento;
import br.com.oficina.domain.model.OrdemServico;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(
    description = "Retorno de uma Ordem de Serviço com itens do orçamento atual.",
    example =
        "{\"numero\":\"OS-042026-000001\",\"idCliente\":42,\"placa\":\"ABC1234\","
            + "\"status\":\"AGUARDANDO_APROVACAO\",\"descricaoProblema\":\"Barulho na suspensão\","
            + "\"valorTotalConserto\":450.00,\"motivoRejeicao\":null,"
            + "\"comprovantePagamento\":null,\"orcamentoAtual\":1,"
            + "\"itens\":[{\"idOrcamento\":1,\"idOrcamentoItem\":1,\"tipoItem\":\"SERVICO\","
            + "\"status\":\"ATIVO\",\"idServicoSku\":1,\"descricao\":\"Troca de óleo\","
            + "\"quantidade\":1,\"precoUnitario\":150.00,\"subtotal\":150.00}],"
            + "\"criadoEm\":\"2026-04-12T13:45:00Z\",\"atualizadoEm\":\"2026-04-12T14:00:00Z\"}")
public record OrdemServicoResponse(
    @Schema(description = "Número da OS no formato OS-MMAAAA-NNNNNN.", example = "OS-042026-000001")
        String numero,
    @Schema(description = "ID do cliente proprietário do veículo.", example = "42") Long idCliente,
    @Schema(description = "Placa do veículo (Mercosul ou antiga).", example = "ABC1234")
        String placa,
    @Schema(
            description =
                "Status atual da OS (EM_DIAGNOSTICO, AGUARDANDO_APROVACAO, EM_EXECUCAO,"
                    + " AGUARDANDO_PAGAMENTO, PAGA, ENTREGUE, CANCELADA).",
            example = "AGUARDANDO_APROVACAO")
        String status,
    @Schema(
            description = "Descrição do problema relatado pelo cliente na abertura.",
            example = "Barulho na suspensão dianteira")
        String descricaoProblema,
    @Schema(description = "Valor total do orçamento atual.", example = "450.00")
        BigDecimal valorTotalConserto,
    @Schema(
            description = "Motivo da rejeição (preenchido apenas se houve rejeição).",
            example = "Valor acima do esperado",
            nullable = true)
        String motivoRejeicao,
    @Schema(
            description = "Identificador do comprovante de pagamento (preenchido após PAGA).",
            example = "PIX-20260412-001",
            nullable = true)
        String comprovantePagamento,
    @Schema(
            description = "Número do orçamento corrente (incrementa a cada rejeição/refazer).",
            example = "1")
        int orcamentoAtual,
    @Schema(description = "Itens (serviços e peças) do orçamento atual.")
        List<ItemOrcamentoResponse> itens,
    @Schema(description = "Data/hora de criação da OS.", example = "2026-04-12T13:45:00Z")
        Instant criadoEm,
    @Schema(description = "Data/hora da última atualização.", example = "2026-04-12T14:00:00Z")
        Instant atualizadoEm) {

  public static OrdemServicoResponse de(OrdemServico os) {
    return new OrdemServicoResponse(
        os.getNumero().valor(),
        os.getIdCliente(),
        os.getPlaca().valor(),
        os.getStatus().name(),
        os.getDescricaoProblema(),
        os.getValorTotalConserto().valor(),
        os.getMotivoRejeicao(),
        os.getComprovantePagamento(),
        os.getOrcamentoAtual(),
        os.getItens().stream().map(ItemOrcamentoResponse::de).toList(),
        os.getCriadoEm(),
        os.getAtualizadoEm());
  }

  @Schema(
      description = "Item (serviço ou peça) de um orçamento de OS.",
      example =
          "{\"idOrcamento\":1,\"idOrcamentoItem\":1,\"tipoItem\":\"SERVICO\","
              + "\"status\":\"ATIVO\",\"idServicoSku\":1,\"descricao\":\"Troca de óleo\","
              + "\"quantidade\":1,\"precoUnitario\":150.00,\"subtotal\":150.00}")
  public record ItemOrcamentoResponse(
      @Schema(description = "Número do orçamento ao qual o item pertence.", example = "1")
          int idOrcamento,
      @Schema(description = "Número sequencial do item dentro do orçamento.", example = "1")
          int idOrcamentoItem,
      @Schema(description = "Tipo do item (SERVICO ou PECA).", example = "SERVICO") String tipoItem,
      @Schema(description = "Status do item no orçamento (ATIVO ou ESTORNADO).", example = "ATIVO")
          String status,
      @Schema(description = "ID do serviço ou SKU da peça (referência ao catálogo).", example = "1")
          Long idServicoSku,
      @Schema(description = "Descrição do serviço ou peça.", example = "Troca de óleo")
          String descricao,
      @Schema(description = "Quantidade do item (>= 1).", example = "1") int quantidade,
      @Schema(description = "Preço unitário aplicado no orçamento.", example = "150.00")
          BigDecimal precoUnitario,
      @Schema(description = "Subtotal do item (quantidade * precoUnitario).", example = "150.00")
          BigDecimal subtotal) {

    public static ItemOrcamentoResponse de(ItemOrcamento i) {
      return new ItemOrcamentoResponse(
          i.getIdOrcamento(),
          i.getIdOrcamentoItem(),
          i.getTipoItem().name(),
          i.getStatus().name(),
          i.getIdServicoSku(),
          i.getDescricao(),
          i.getQuantidade(),
          i.getPrecoUnitario().valor(),
          i.subtotal().valor());
    }
  }
}
