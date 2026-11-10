package br.com.oficina.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "itens_nota_fiscal_fornecedor")
@IdClass(ItemNfIdJpa.class)
public class ItemNotaFiscalFornecedorJpaEntity {

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

  @Id
  @Column(name = "id_sku", nullable = false)
  private Long idSku;

  @Column(nullable = false)
  private int quantidade;

  @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
  private BigDecimal precoUnitario;

  protected ItemNotaFiscalFornecedorJpaEntity() {}

  public ItemNotaFiscalFornecedorJpaEntity(
      String numeroNota,
      String serieNota,
      String cnpjFornecedor,
      LocalDate dataEmissao,
      Long idSku,
      int quantidade,
      BigDecimal precoUnitario) {
    this.numeroNota = numeroNota;
    this.serieNota = serieNota;
    this.cnpjFornecedor = cnpjFornecedor;
    this.dataEmissao = dataEmissao;
    this.idSku = idSku;
    this.quantidade = quantidade;
    this.precoUnitario = precoUnitario;
  }

  public Long getIdSku() {
    return idSku;
  }

  public int getQuantidade() {
    return quantidade;
  }

  public BigDecimal getPrecoUnitario() {
    return precoUnitario;
  }
}
