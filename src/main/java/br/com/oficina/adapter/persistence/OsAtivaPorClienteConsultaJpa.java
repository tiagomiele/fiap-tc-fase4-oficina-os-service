package br.com.oficina.adapter.persistence;

import br.com.oficina.usecase.gateway.OsAtivaPorClienteConsulta;
import org.springframework.stereotype.Component;

@Component
public class OsAtivaPorClienteConsultaJpa implements OsAtivaPorClienteConsulta {

  private final SpringDataOrdemServicoRepository repo;

  public OsAtivaPorClienteConsultaJpa(SpringDataOrdemServicoRepository repo) {
    this.repo = repo;
  }

  @Override
  public boolean temOsAtiva(Long idCliente) {
    return repo.existeOsAtivaPorCliente(idCliente);
  }
}
