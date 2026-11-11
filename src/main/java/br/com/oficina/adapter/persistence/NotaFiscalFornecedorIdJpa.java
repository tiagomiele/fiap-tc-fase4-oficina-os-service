package br.com.oficina.adapter.persistence;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class NotaFiscalFornecedorIdJpa implements Serializable {

  private String numeroNota;
  private String serieNota;
  private String cnpjFornecedor;
  private LocalDate dataEmissao;

  public NotaFiscalFornecedorIdJpa() {}

  public NotaFiscalFornecedorIdJpa(
      String numeroNota, String serieNota, String cnpjFornecedor, LocalDate dataEmissao) {
    this.numeroNota = numeroNota;
    this.serieNota = serieNota;
    this.cnpjFornecedor = cnpjFornecedor;
    this.dataEmissao = dataEmissao;
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

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NotaFiscalFornecedorIdJpa other)) return false;
    return Objects.equals(numeroNota, other.numeroNota)
        && Objects.equals(serieNota, other.serieNota)
        && Objects.equals(cnpjFornecedor, other.cnpjFornecedor)
        && Objects.equals(dataEmissao, other.dataEmissao);
  }

  @Override
  public int hashCode() {
    return Objects.hash(numeroNota, serieNota, cnpjFornecedor, dataEmissao);
  }
}
