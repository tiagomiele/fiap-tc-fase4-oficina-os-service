package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.EstoquePeca;
import br.com.oficina.domain.model.MovimentacaoEstoque;
import br.com.oficina.usecase.gateway.EstoqueRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaEstoqueRepository implements EstoqueRepository {

  private final SpringDataEstoqueRepository saldos;
  private final SpringDataMovimentacaoRepository movs;

  public JpaEstoqueRepository(
      SpringDataEstoqueRepository saldos, SpringDataMovimentacaoRepository movs) {
    this.saldos = saldos;
    this.movs = movs;
  }

  @Override
  public EstoquePeca salvar(EstoquePeca peca) {
    EstoquePecaJpaEntity entity =
        saldos
            .findById(peca.getIdSku())
            .map(
                existing -> {
                  existing.atualizarDe(peca);
                  return existing;
                })
            .orElseGet(() -> new EstoquePecaJpaEntity(peca));
    return saldos.save(entity).toDomain();
  }

  @Override
  public Optional<EstoquePeca> porSku(Long idSku) {
    return saldos.findById(idSku).map(EstoquePecaJpaEntity::toDomain);
  }

  @Override
  public List<EstoquePeca> listarTodos() {
    return saldos.findAll().stream().map(EstoquePecaJpaEntity::toDomain).toList();
  }

  @Override
  public MovimentacaoEstoque registrar(MovimentacaoEstoque mov) {
    MovimentacaoEstoqueJpaEntity entity = new MovimentacaoEstoqueJpaEntity(mov);
    MovimentacaoEstoqueJpaEntity saved = movs.save(entity);
    mov.definirId(saved.getId());
    return saved.toDomain();
  }
}
