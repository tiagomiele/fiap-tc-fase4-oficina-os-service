package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.enums.TipoLancamento;
import br.com.oficina.domain.model.LancamentoFinanceiro;
import br.com.oficina.usecase.gateway.LancamentoFinanceiroRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaLancamentoRepository implements LancamentoFinanceiroRepository {

  private final SpringDataLancamentoRepository repo;

  public JpaLancamentoRepository(SpringDataLancamentoRepository repo) {
    this.repo = repo;
  }

  @Override
  public LancamentoFinanceiro salvar(LancamentoFinanceiro l) {
    LancamentoFinanceiroJpaEntity entity =
        l.getId() == null
            ? new LancamentoFinanceiroJpaEntity(l)
            : repo.findById(l.getId())
                .map(
                    existing -> {
                      existing.atualizarDe(l);
                      return existing;
                    })
                .orElseGet(() -> new LancamentoFinanceiroJpaEntity(l));
    LancamentoFinanceiroJpaEntity saved = repo.save(entity);
    l.definirId(saved.getId());
    return saved.toDomain();
  }

  @Override
  public Optional<LancamentoFinanceiro> porId(Long id) {
    return repo.findById(id).map(LancamentoFinanceiroJpaEntity::toDomain);
  }

  @Override
  public List<LancamentoFinanceiro> porTipo(TipoLancamento tipo) {
    return repo.findByTipoOrderByDataLancamentoDesc(tipo).stream()
        .map(LancamentoFinanceiroJpaEntity::toDomain)
        .toList();
  }

  @Override
  public Optional<LancamentoFinanceiro> porNf(
      String numero, String serie, String cnpj, LocalDate dataEmissao) {
    return repo.findByNumeroNotaAndSerieNotaAndCnpjFornecedorAndDataEmissao(
            numero, serie, cnpj, dataEmissao)
        .map(LancamentoFinanceiroJpaEntity::toDomain);
  }
}
