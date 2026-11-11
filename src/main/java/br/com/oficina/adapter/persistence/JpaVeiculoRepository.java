package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.Veiculo;
import br.com.oficina.domain.model.VeiculoId;
import br.com.oficina.usecase.gateway.OsAtivaPorVeiculoConsulta;
import br.com.oficina.usecase.gateway.VeiculoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaVeiculoRepository implements VeiculoRepository {

  private final SpringDataVeiculoRepository repo;
  private final OsAtivaPorVeiculoConsulta osAtiva;

  public JpaVeiculoRepository(SpringDataVeiculoRepository repo, OsAtivaPorVeiculoConsulta osAtiva) {
    this.repo = repo;
    this.osAtiva = osAtiva;
  }

  @Override
  public Veiculo salvar(Veiculo veiculo) {
    VeiculoIdJpa idJpa =
        new VeiculoIdJpa(veiculo.getId().placa().valor(), veiculo.getId().idCliente());
    VeiculoJpaEntity entity =
        repo.findById(idJpa)
            .map(
                existing -> {
                  existing.atualizarDe(veiculo);
                  return existing;
                })
            .orElseGet(() -> new VeiculoJpaEntity(veiculo));
    return repo.save(entity).toDomain();
  }

  @Override
  public Optional<Veiculo> porId(VeiculoId id) {
    return repo.findById(new VeiculoIdJpa(id.placa().valor(), id.idCliente()))
        .map(VeiculoJpaEntity::toDomain);
  }

  @Override
  public List<Veiculo> porCliente(Long idCliente) {
    return repo.findByIdCliente(idCliente).stream().map(VeiculoJpaEntity::toDomain).toList();
  }

  @Override
  public boolean temOsAtiva(VeiculoId id) {
    return osAtiva.temOsAtiva(id.placa().valor(), id.idCliente());
  }
}
