package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.enums.OrigemLancamento;
import br.com.oficina.domain.enums.TipoLancamento;
import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.LancamentoFinanceiro;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "conta_corrente_oficina")
public class LancamentoFinanceiroJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private TipoLancamento tipo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OrigemLancamento origem;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal valor;

  @Column(name = "data_lancamento", nullable = false)
  private LocalDate dataLancamento;

  @Column(length = 500)
  private String descricao;

  @Column(name = "numero_nota", length = 30)
  private String numeroNota;

  @Column(name = "serie_nota", length = 10)
  private String serieNota;

  @Column(name = "cnpj_fornecedor", length = 20)
  private String cnpjFornecedor;

  @Column(name = "data_emissao")
  private LocalDate dataEmissao;

  @Column(name = "id_ordem_servico", length = 20)
  private String idOrdemServico;

  @Column(nullable = false)
  private boolean estornado;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private Instant atualizadoEm;

  protected LancamentoFinanceiroJpaEntity() {}

  public LancamentoFinanceiroJpaEntity(LancamentoFinanceiro l) {
    atualizarDe(l);
    this.criadoEm = l.getCriadoEm();
  }

  public void atualizarDe(LancamentoFinanceiro l) {
    this.id = l.getId();
    this.tipo = l.getTipo();
    this.origem = l.getOrigem();
    this.valor = l.getValor().valor();
    this.dataLancamento = l.getDataLancamento();
    this.descricao = l.getDescricao();
    this.numeroNota = l.getNumeroNota();
    this.serieNota = l.getSerieNota();
    this.cnpjFornecedor = l.getCnpjFornecedor();
    this.dataEmissao = l.getDataEmissao();
    this.idOrdemServico = l.getIdOrdemServico();
    this.estornado = l.isEstornado();
    this.atualizadoEm = l.getAtualizadoEm();
  }

  public LancamentoFinanceiro toDomain() {
    return LancamentoFinanceiro.reconstituir(
        id,
        tipo,
        origem,
        Dinheiro.de(valor),
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

  public Long getId() {
    return id;
  }
}
