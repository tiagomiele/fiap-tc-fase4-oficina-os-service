package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.model.Veiculo;
import br.com.oficina.domain.model.VeiculoId;
import java.util.List;
import java.util.Optional;

public interface VeiculoRepository {
  Veiculo salvar(Veiculo veiculo);

  Optional<Veiculo> porId(VeiculoId id);

  List<Veiculo> porCliente(Long idCliente);

  boolean temOsAtiva(VeiculoId id);
}
