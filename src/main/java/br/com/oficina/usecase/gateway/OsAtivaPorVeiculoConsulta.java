package br.com.oficina.usecase.gateway;

public interface OsAtivaPorVeiculoConsulta {
  boolean temOsAtiva(String placa, Long idCliente);
}
