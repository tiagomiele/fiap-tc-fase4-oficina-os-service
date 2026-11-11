package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.model.NumeroOS;
import br.com.oficina.domain.model.OrdemServico;
import java.util.List;
import java.util.Optional;

public interface OrdemServicoRepository {
  OrdemServico salvar(OrdemServico os);

  Optional<OrdemServico> porNumero(NumeroOS numero);

  List<OrdemServico> listar();
}
