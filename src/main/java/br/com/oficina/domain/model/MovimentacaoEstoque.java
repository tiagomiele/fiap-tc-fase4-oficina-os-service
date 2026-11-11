package br.com.oficina.domain.model;

import br.com.oficina.domain.enums.OrigemMovimentacao;
import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;

public class MovimentacaoEstoque {

  private static final String ERRO_MOVIMENTACAO_INVALIDA = "MOVIMENTACAO_INVALIDA";

  private Long id;
  private final Long idSku;
  private final int quantidade;
  private final OrigemMovimentacao origem;
  private final String numeroNota;
  private final String serieNota;
  private final String cnpjFornecedor;
  private final LocalDate dataEmissao;
  private final String idOrdemServico;
  private final Integer idOrcamento;
  private final Integer idOrcamentoItem;
  private final Instant dataHora;

  private MovimentacaoEstoque(Builder b) {
    if (b.idSku == null) {
      throw new BusinessException(ERRO_MOVIMENTACAO_INVALIDA, "idSku obrigatório");
    }
    if (b.quantidade == 0) {
      throw new BusinessException(ERRO_MOVIMENTACAO_INVALIDA, "Quantidade não pode ser zero");
    }
    if (b.origem == null) {
      throw new BusinessException(ERRO_MOVIMENTACAO_INVALIDA, "Origem obrigatória");
    }
    this.id = b.id;
    this.idSku = b.idSku;
    this.quantidade = b.quantidade;
    this.origem = b.origem;
    this.numeroNota = b.numeroNota;
    this.serieNota = b.serieNota;
    this.cnpjFornecedor = b.cnpjFornecedor;
    this.dataEmissao = b.dataEmissao;
    this.idOrdemServico = b.idOrdemServico;
    this.idOrcamento = b.idOrcamento;
    this.idOrcamentoItem = b.idOrcamentoItem;
    this.dataHora = b.dataHora == null ? Instant.now() : b.dataHora;
  }

  public static Builder entradaPorNf(Long idSku, int quantidade) {
    return new Builder().idSku(idSku).quantidade(quantidade).origem(OrigemMovimentacao.ENTRADA_NF);
  }

  public static Builder estornoPorNf(Long idSku, int quantidade) {
    return new Builder().idSku(idSku).quantidade(-quantidade).origem(OrigemMovimentacao.ESTORNO_NF);
  }

  public static Builder consumoPorOrcamento(Long idSku, int quantidade) {
    return new Builder()
        .idSku(idSku)
        .quantidade(-quantidade)
        .origem(OrigemMovimentacao.CONSUMO_ORCAMENTO);
  }

  public static Builder devolucaoPorOrcamento(Long idSku, int quantidade) {
    return new Builder()
        .idSku(idSku)
        .quantidade(quantidade)
        .origem(OrigemMovimentacao.DEVOLUCAO_ORCAMENTO);
  }

  public Long getId() {
    return id;
  }

  public void definirId(Long id) {
    this.id = id;
  }

  public Long getIdSku() {
    return idSku;
  }

  public int getQuantidade() {
    return quantidade;
  }

  public OrigemMovimentacao getOrigem() {
    return origem;
  }

  public String getNumeroNota() {
    return numeroNota;
  }

  public String getSerieNota() {
    return serieNota;
  }

  public String getCnpjFornecedor() {
    return cnpjFornecedor;
  }

  public LocalDate getDataEmissao() {
    return dataEmissao;
  }

  public String getIdOrdemServico() {
    return idOrdemServico;
  }

  public Integer getIdOrcamento() {
    return idOrcamento;
  }

  public Integer getIdOrcamentoItem() {
    return idOrcamentoItem;
  }

  public Instant getDataHora() {
    return dataHora;
  }

  public static class Builder {
    private Long id;
    private Long idSku;
    private int quantidade;
    private OrigemMovimentacao origem;
    private String numeroNota;
    private String serieNota;
    private String cnpjFornecedor;
    private LocalDate dataEmissao;
    private String idOrdemServico;
    private Integer idOrcamento;
    private Integer idOrcamentoItem;
    private Instant dataHora;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder idSku(Long id) {
      this.idSku = id;
      return this;
    }

    public Builder quantidade(int q) {
      this.quantidade = q;
      return this;
    }

    public Builder origem(OrigemMovimentacao o) {
      this.origem = o;
      return this;
    }

    public Builder nota(String numero, String serie, String cnpj, LocalDate dataEmissao) {
      this.numeroNota = numero;
      this.serieNota = serie;
      this.cnpjFornecedor = cnpj;
      this.dataEmissao = dataEmissao;
      return this;
    }

    public Builder orcamento(String idOs, Integer idOrc, Integer idItem) {
      this.idOrdemServico = idOs;
      this.idOrcamento = idOrc;
      this.idOrcamentoItem = idItem;
      return this;
    }

    public Builder dataHora(Instant dh) {
      this.dataHora = dh;
      return this;
    }

    public MovimentacaoEstoque construir() {
      return new MovimentacaoEstoque(this);
    }
  }
}
