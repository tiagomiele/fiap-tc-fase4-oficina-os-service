package br.com.oficina.domain.model;

import java.util.Objects;

public record VeiculoId(Placa placa, Long idCliente) {

  public VeiculoId {
    Objects.requireNonNull(placa, "placa obrigatória");
    Objects.requireNonNull(idCliente, "idCliente obrigatório");
  }
}
