package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.Peca;
import br.com.oficina.usecase.gateway.OsAtivaPorPecaConsulta;
import br.com.oficina.usecase.gateway.PecaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaPecaRepository implements PecaRepository {

  private final SpringDataPecaRepository repo;
  private final OsAtivaPorPecaConsulta osAtiva;

  public JpaPecaRepository(SpringDataPecaRepository repo, OsAtivaPorPecaConsulta osAtiva) {
    this.repo = repo;
    this.osAtiva = osAtiva;
  }

  @Override
  public Peca salvar(Peca p) {
    PecaJpaEntity entity =
        p.getIdSku() == null
            ? new PecaJpaEntity(p)
            : repo.findById(p.getIdSku())
                .map(
                    existing -> {
                      existing.atualizarDe(p);
                      return existing;
                    })
                .orElseGet(() -> new PecaJpaEntity(p));
    PecaJpaEntity saved = repo.save(entity);
    p.definirId(saved.getIdSku());
    return saved.toDomain();
  }

  @Override
  public Optional<Peca> porSku(Long id) {
    return repo.findById(id).map(PecaJpaEntity::toDomain);
  }

  @Override
  public List<Peca> listarTodas() {
    return repo.findAll().stream().map(PecaJpaEntity::toDomain).toList();
  }

  @Override
  public boolean temOsAtiva(Long idSku) {
    return osAtiva.temOsAtiva(idSku);
  }
}
