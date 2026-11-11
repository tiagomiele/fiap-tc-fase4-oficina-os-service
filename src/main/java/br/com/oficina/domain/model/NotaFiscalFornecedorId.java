package br.com.oficina.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public record NotaFiscalFornecedorId(
    String numeroNota, String serieNota, String cnpjFornecedor, LocalDate dataEmissao) {

  public NotaFiscalFornecedorId {
    Objects.requireNonNull(numeroNota);
    Objects.requireNonNull(serieNota);
    Objects.requireNonNull(cnpjFornecedor);
    Objects.requireNonNull(dataEmissao);
  }
}
