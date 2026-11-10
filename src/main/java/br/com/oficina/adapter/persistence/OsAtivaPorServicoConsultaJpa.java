package br.com.oficina.adapter.persistence;

import br.com.oficina.usecase.gateway.OsAtivaPorServicoConsulta;
import org.springframework.stereotype.Component;

@Component
public class OsAtivaPorServicoConsultaJpa implements OsAtivaPorServicoConsulta {

  private final SpringDataOrdemServicoRepository repo;

  public OsAtivaPorServicoConsultaJpa(SpringDataOrdemServicoRepository repo) {
    this.repo = repo;
  }

  @Override
  public boolean temOsAtiva(Long idServico) {
    return repo.existeOsAtivaPorServicoSku(idServico, "SERVICO");
  }
}
