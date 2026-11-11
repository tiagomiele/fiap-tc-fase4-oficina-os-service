package br.com.oficina.adapter.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notas_fiscais_fornecedor")
@IdClass(NotaFiscalFornecedorIdJpa.class)
public class NotaFiscalFornecedorJpaEntity {

  @Id
  @Column(name = "numero_nota", length = 30, nullable = false)
  private String numeroNota;

  @Id
  @Column(name = "serie_nota", length = 10, nullable = false)
  private String serieNota;

  @Id
  @Column(name = "cnpj_fornecedor", length = 20, nullable = false)
  private String cnpjFornecedor;

  @Id
  @Column(name = "data_emissao", nullable = false)
  private LocalDate dataEmissao;

  @Column(name = "nome_fornecedor", nullable = false)
  private String nomeFornecedor;

  @Column(name = "valor_total", nullable = false, precision = 14, scale = 2)
  private BigDecimal valorTotal;

  @Column(nullable = false)
  private boolean estornada;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private Instant atualizadoEm;

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
  @JoinColumn(
      name = "numero_nota",
      referencedColumnName = "numero_nota",
      insertable = false,
      updatable = false)
  @JoinColumn(
      name = "serie_nota",
      referencedColumnName = "serie_nota",
      insertable = false,
      updatable = false)
  @JoinColumn(
      name = "cnpj_fornecedor",
      referencedColumnName = "cnpj_fornecedor",
      insertable = false,
      updatable = false)
  @JoinColumn(
      name = "data_emissao",
      referencedColumnName = "data_emissao",
      insertable = false,
      updatable = false)
  private List<ItemNotaFiscalFornecedorJpaEntity> itens = new ArrayList<>();

  protected NotaFiscalFornecedorJpaEntity() {}

  @SuppressWarnings("java:S107") // Reidratação da entidade JPA requer todos os atributos.
  public NotaFiscalFornecedorJpaEntity(
      String numeroNota,
      String serieNota,
      String cnpjFornecedor,
      LocalDate dataEmissao,
      String nomeFornecedor,
      BigDecimal valorTotal,
      boolean estornada,
      Instant criadoEm,
      Instant atualizadoEm,
      List<ItemNotaFiscalFornecedorJpaEntity> itens) {
    this.numeroNota = numeroNota;
    this.serieNota = serieNota;
    this.cnpjFornecedor = cnpjFornecedor;
    this.dataEmissao = dataEmissao;
    this.nomeFornecedor = nomeFornecedor;
    this.valorTotal = valorTotal;
    this.estornada = estornada;
    this.criadoEm = criadoEm;
    this.atualizadoEm = atualizadoEm;
    this.itens = itens == null ? new ArrayList<>() : new ArrayList<>(itens);
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

  public String getNomeFornecedor() {
    return nomeFornecedor;
  }

  public BigDecimal getValorTotal() {
    return valorTotal;
  }

  public boolean isEstornada() {
    return estornada;
  }

  public void setEstornada(boolean estornada) {
    this.estornada = estornada;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public Instant getAtualizadoEm() {
    return atualizadoEm;
  }

  public void setAtualizadoEm(Instant i) {
    this.atualizadoEm = i;
  }

  public List<ItemNotaFiscalFornecedorJpaEntity> getItens() {
    return itens;
  }
}
