package br.com.oficina.adapter.persistence;

import br.com.oficina.usecase.gateway.OsAtivaPorPecaConsulta;
import org.springframework.stereotype.Component;

@Component
public class OsAtivaPorPecaConsultaJpa implements OsAtivaPorPecaConsulta {

  private final SpringDataOrdemServicoRepository repo;

  public OsAtivaPorPecaConsultaJpa(SpringDataOrdemServicoRepository repo) {
    this.repo = repo;
  }

  @Override
  public boolean temOsAtiva(Long idSku) {
    return repo.existeOsAtivaPorServicoSku(idSku, "PECA");
  }
}
