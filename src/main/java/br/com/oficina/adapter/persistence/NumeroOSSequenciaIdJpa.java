package br.com.oficina.adapter.persistence;

import java.io.Serializable;
import java.util.Objects;

public class NumeroOSSequenciaIdJpa implements Serializable {

  private int mes;
  private int ano;

  public NumeroOSSequenciaIdJpa() {}

  public NumeroOSSequenciaIdJpa(int mes, int ano) {
    this.mes = mes;
    this.ano = ano;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NumeroOSSequenciaIdJpa other)) return false;
    return mes == other.mes && ano == other.ano;
  }

  @Override
  public int hashCode() {
    return Objects.hash(mes, ano);
  }
}
