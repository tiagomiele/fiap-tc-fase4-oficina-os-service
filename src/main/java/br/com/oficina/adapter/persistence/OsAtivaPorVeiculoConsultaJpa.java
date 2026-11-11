package br.com.oficina.adapter.persistence;

import br.com.oficina.usecase.gateway.OsAtivaPorVeiculoConsulta;
import org.springframework.stereotype.Component;

@Component
public class OsAtivaPorVeiculoConsultaJpa implements OsAtivaPorVeiculoConsulta {

  private final SpringDataOrdemServicoRepository repo;

  public OsAtivaPorVeiculoConsultaJpa(SpringDataOrdemServicoRepository repo) {
    this.repo = repo;
  }

  @Override
  public boolean temOsAtiva(String placa, Long idCliente) {
    return repo.existeOsAtivaPorVeiculo(placa, idCliente);
  }
}
