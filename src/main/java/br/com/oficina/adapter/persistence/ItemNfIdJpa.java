package br.com.oficina.adapter.persistence;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class ItemNfIdJpa implements Serializable {

  private String numeroNota;
  private String serieNota;
  private String cnpjFornecedor;
  private LocalDate dataEmissao;
  private Long idSku;

  public ItemNfIdJpa() {}

  public ItemNfIdJpa(
      String numeroNota,
      String serieNota,
      String cnpjFornecedor,
      LocalDate dataEmissao,
      Long idSku) {
    this.numeroNota = numeroNota;
    this.serieNota = serieNota;
    this.cnpjFornecedor = cnpjFornecedor;
    this.dataEmissao = dataEmissao;
    this.idSku = idSku;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ItemNfIdJpa other)) return false;
    return Objects.equals(numeroNota, other.numeroNota)
        && Objects.equals(serieNota, other.serieNota)
        && Objects.equals(cnpjFornecedor, other.cnpjFornecedor)
        && Objects.equals(dataEmissao, other.dataEmissao)
        && Objects.equals(idSku, other.idSku);
  }

  @Override
  public int hashCode() {
    return Objects.hash(numeroNota, serieNota, cnpjFornecedor, dataEmissao, idSku);
  }
}
