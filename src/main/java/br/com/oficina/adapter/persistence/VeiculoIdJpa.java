package br.com.oficina.adapter.persistence;

import java.io.Serializable;
import java.util.Objects;

public class VeiculoIdJpa implements Serializable {

  private String idPlaca;
  private Long idCliente;

  public VeiculoIdJpa() {}

  public VeiculoIdJpa(String idPlaca, Long idCliente) {
    this.idPlaca = idPlaca;
    this.idCliente = idCliente;
  }

  public String getIdPlaca() {
    return idPlaca;
  }

  public Long getIdCliente() {
    return idCliente;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof VeiculoIdJpa other)) return false;
    return Objects.equals(idPlaca, other.idPlaca) && Objects.equals(idCliente, other.idCliente);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idPlaca, idCliente);
  }
}
