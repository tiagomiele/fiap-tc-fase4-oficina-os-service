package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.Cliente;
import br.com.oficina.domain.model.Documento;
import br.com.oficina.usecase.gateway.ClienteRepository;
import br.com.oficina.usecase.gateway.OsAtivaPorClienteConsulta;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaClienteRepository implements ClienteRepository {

  private final SpringDataClienteRepository repo;
  private final OsAtivaPorClienteConsulta osAtiva;

  public JpaClienteRepository(SpringDataClienteRepository repo, OsAtivaPorClienteConsulta osAtiva) {
    this.repo = repo;
    this.osAtiva = osAtiva;
  }

  @Override
  public Cliente salvar(Cliente cliente) {
    ClienteJpaEntity entity =
        cliente.getIdCliente() == null
            ? new ClienteJpaEntity(cliente)
            : repo.findById(cliente.getIdCliente())
                .map(
                    existing -> {
                      existing.atualizarDe(cliente);
                      return existing;
                    })
                .orElseGet(() -> new ClienteJpaEntity(cliente));
    ClienteJpaEntity saved = repo.save(entity);
    cliente.definirId(saved.getIdCliente());
    return saved.toDomain();
  }

  @Override
  public Optional<Cliente> porId(Long id) {
    return repo.findById(id).map(ClienteJpaEntity::toDomain);
  }

  @Override
  public Optional<Cliente> porDocumento(Documento d) {
    return repo.findByDocumento(d.valor()).map(ClienteJpaEntity::toDomain);
  }

  @Override
  public boolean existePorDocumento(Documento d) {
    return repo.existsByDocumento(d.valor());
  }

  @Override
  public List<Cliente> listarTodos() {
    return repo.findAll().stream().map(ClienteJpaEntity::toDomain).toList();
  }

  @Override
  public boolean temOsAtiva(Long idCliente) {
    return osAtiva.temOsAtiva(idCliente);
  }
}
