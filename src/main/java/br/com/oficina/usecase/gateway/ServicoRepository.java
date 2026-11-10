package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.model.Servico;
import java.util.List;
import java.util.Optional;

public interface ServicoRepository {
  Servico salvar(Servico servico);

  Optional<Servico> porId(Long id);

  List<Servico> listarTodos();

  boolean temOsAtiva(Long idServico);
}
