package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.Servico;
import br.com.oficina.usecase.gateway.OsAtivaPorServicoConsulta;
import br.com.oficina.usecase.gateway.ServicoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaServicoRepository implements ServicoRepository {

  private final SpringDataServicoRepository repo;
  private final OsAtivaPorServicoConsulta osAtiva;

  public JpaServicoRepository(SpringDataServicoRepository repo, OsAtivaPorServicoConsulta osAtiva) {
    this.repo = repo;
    this.osAtiva = osAtiva;
  }

  @Override
  public Servico salvar(Servico s) {
    ServicoJpaEntity entity =
        s.getIdServico() == null
            ? new ServicoJpaEntity(s)
            : repo.findById(s.getIdServico())
                .map(
                    existing -> {
                      existing.atualizarDe(s);
                      return existing;
                    })
                .orElseGet(() -> new ServicoJpaEntity(s));
    ServicoJpaEntity saved = repo.save(entity);
    s.definirId(saved.getIdServico());
    return saved.toDomain();
  }

  @Override
  public Optional<Servico> porId(Long id) {
    return repo.findById(id).map(ServicoJpaEntity::toDomain);
  }

  @Override
  public List<Servico> listarTodos() {
    return repo.findAll().stream().map(ServicoJpaEntity::toDomain).toList();
  }

  @Override
  public boolean temOsAtiva(Long idServico) {
    return osAtiva.temOsAtiva(idServico);
  }
}
