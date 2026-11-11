package br.com.oficina.domain.model;

import br.com.oficina.domain.enums.OrigemLancamento;
import br.com.oficina.domain.enums.TipoLancamento;
import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public class LancamentoFinanceiro {

  private Long id;
  private final TipoLancamento tipo;
  private final OrigemLancamento origem;
  private final Dinheiro valor;
  private final LocalDate dataLancamento;
  private String descricao;
  private String numeroNota;
  private String serieNota;
  private String cnpjFornecedor;
  private LocalDate dataEmissao;
  private String idOrdemServico;
  private boolean estornado;
  private final Instant criadoEm;
  private Instant atualizadoEm;

  @SuppressWarnings(
      "java:S107") // Reconstituição de lançamento financeiro requer todos os atributos.
  private LancamentoFinanceiro(
      Long id,
      TipoLancamento tipo,
      OrigemLancamento origem,
      Dinheiro valor,
      LocalDate dataLancamento,
      String descricao,
      String numeroNota,
      String serieNota,
      String cnpjFornecedor,
      LocalDate dataEmissao,
      String idOrdemServico,
      boolean estornado,
      Instant criadoEm,
      Instant atualizadoEm) {
    if (valor == null) {
      throw new BusinessException("LANCAMENTO_INVALIDO", "Valor obrigatório");
    }
    this.id = id;
    this.tipo = Objects.requireNonNull(tipo, "tipo");
    this.origem = Objects.requireNonNull(origem, "origem");
    this.valor = valor;
    this.dataLancamento = dataLancamento == null ? LocalDate.now() : dataLancamento;
    this.descricao = descricao;
    this.numeroNota = numeroNota;
    this.serieNota = serieNota;
    this.cnpjFornecedor = cnpjFornecedor;
    this.dataEmissao = dataEmissao;
    this.idOrdemServico = idOrdemServico;
    this.estornado = estornado;
    this.criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    this.atualizadoEm = atualizadoEm == null ? this.criadoEm : atualizadoEm;
  }

  public static LancamentoFinanceiro contaAPagarNF(
      Dinheiro valor,
      String numeroNota,
      String serieNota,
      String cnpjFornecedor,
      LocalDate dataEmissao,
      String descricao) {
    return new LancamentoFinanceiro(
        null,
        TipoLancamento.CONTAS_A_PAGAR,
        OrigemLancamento.NF_FORNECEDOR,
        valor,
        LocalDate.now(),
        descricao,
        numeroNota,
        serieNota,
        cnpjFornecedor,
        dataEmissao,
        null,
        false,
        Instant.now(),
        Instant.now());
  }

  public static LancamentoFinanceiro contaAReceberOS(
      Dinheiro valor, String idOrdemServico, String descricao) {
    return new LancamentoFinanceiro(
        null,
        TipoLancamento.CONTAS_A_RECEBER,
        OrigemLancamento.OS_PAGAMENTO,
        valor,
        LocalDate.now(),
        descricao,
        null,
        null,
        null,
        null,
        idOrdemServico,
        false,
        Instant.now(),
        Instant.now());
  }

  public void estornar() {
    if (estornado) {
      throw new BusinessException("LANCAMENTO_JA_ESTORNADO", "Lançamento já estornado");
    }
    this.estornado = true;
    this.atualizadoEm = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public void definirId(Long id) {
    this.id = id;
  }

  public TipoLancamento getTipo() {
    return tipo;
  }

  public OrigemLancamento getOrigem() {
    return origem;
  }

  public Dinheiro getValor() {
    return valor;
  }

  public LocalDate getDataLancamento() {
    return dataLancamento;
  }

  public String getDescricao() {
    return descricao;
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

  public boolean isEstornado() {
    return estornado;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public Instant getAtualizadoEm() {
    return atualizadoEm;
  }

  @SuppressWarnings(
      "java:S107") // Reconstituição de lançamento financeiro requer todos os atributos.
  public static LancamentoFinanceiro reconstituir(
      Long id,
      TipoLancamento tipo,
      OrigemLancamento origem,
      Dinheiro valor,
      LocalDate dataLancamento,
      String descricao,
      String numeroNota,
      String serieNota,
      String cnpjFornecedor,
      LocalDate dataEmissao,
      String idOrdemServico,
      boolean estornado,
      Instant criadoEm,
      Instant atualizadoEm) {
    return new LancamentoFinanceiro(
        id,
        tipo,
        origem,
        valor,
        dataLancamento,
        descricao,
        numeroNota,
        serieNota,
        cnpjFornecedor,
        dataEmissao,
        idOrdemServico,
        estornado,
        criadoEm,
        atualizadoEm);
  }
}
