package br.com.oficina.adapter.dto;

import br.com.oficina.domain.model.LancamentoFinanceiro;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(
    description =
        "Lançamento financeiro (Conta a Receber ou Conta a Pagar). Campos da nota fiscal são"
            + " preenchidos apenas quando `origem`=`NOTA_FORNECEDOR`; `idOrdemServico` é"
            + " preenchido apenas quando `origem`=`OS`.",
    example =
        "{\"id\":1,\"tipo\":\"A_RECEBER\",\"origem\":\"OS\",\"valor\":450.00,"
            + "\"dataLancamento\":\"2026-04-12\","
            + "\"descricao\":\"Pagamento da OS OS-042026-000001\","
            + "\"numeroNota\":null,\"serieNota\":null,\"cnpjFornecedor\":null,"
            + "\"dataEmissao\":null,\"idOrdemServico\":\"OS-042026-000001\","
            + "\"estornado\":false}")
public record LancamentoResponse(
    @Schema(description = "ID interno do lançamento.", example = "1") Long id,
    @Schema(description = "Tipo do lançamento (A_RECEBER ou A_PAGAR).", example = "A_RECEBER")
        String tipo,
    @Schema(
            description =
                "Origem do lançamento (OS para Conta a Receber, NOTA_FORNECEDOR para Conta a"
                    + " Pagar).",
            example = "OS")
        String origem,
    @Schema(description = "Valor do lançamento.", example = "450.00") BigDecimal valor,
    @Schema(description = "Data de competência do lançamento (ISO).", example = "2026-04-12")
        LocalDate dataLancamento,
    @Schema(
            description = "Descrição livre do lançamento.",
            example = "Pagamento da OS OS-042026-000001")
        String descricao,
    @Schema(
            description = "Número da nota fiscal (apenas quando origem=NOTA_FORNECEDOR).",
            example = "123456",
            nullable = true)
        String numeroNota,
    @Schema(
            description = "Série da nota fiscal (apenas quando origem=NOTA_FORNECEDOR).",
            example = "1",
            nullable = true)
        String serieNota,
    @Schema(
            description = "CNPJ do fornecedor (14 dígitos, apenas quando origem=NOTA_FORNECEDOR).",
            example = "12345678000190",
            nullable = true)
        String cnpjFornecedor,
    @Schema(
            description = "Data de emissão da nota (apenas quando origem=NOTA_FORNECEDOR).",
            example = "2026-04-01",
            nullable = true)
        LocalDate dataEmissao,
    @Schema(
            description = "Número da Ordem de Serviço (apenas quando origem=OS).",
            example = "OS-042026-000001",
            nullable = true)
        String idOrdemServico,
    @Schema(
            description = "Indica se o lançamento foi estornado (true após reversão).",
            example = "false")
        boolean estornado) {

  public static LancamentoResponse de(LancamentoFinanceiro l) {
    return new LancamentoResponse(
        l.getId(),
        l.getTipo().name(),
        l.getOrigem().name(),
        l.getValor().valor(),
        l.getDataLancamento(),
        l.getDescricao(),
        l.getNumeroNota(),
        l.getSerieNota(),
        l.getCnpjFornecedor(),
        l.getDataEmissao(),
        l.getIdOrdemServico(),
        l.isEstornado());
  }
}
