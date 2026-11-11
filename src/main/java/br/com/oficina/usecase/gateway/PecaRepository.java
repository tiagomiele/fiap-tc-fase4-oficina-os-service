package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.model.Peca;
import java.util.List;
import java.util.Optional;

public interface PecaRepository {
  Peca salvar(Peca peca);

  Optional<Peca> porSku(Long idSku);

  List<Peca> listarTodas();

  boolean temOsAtiva(Long idSku);
}
