package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.ItemNotaFiscalFornecedor;
import br.com.oficina.domain.model.NotaFiscalFornecedor;
import br.com.oficina.domain.model.NotaFiscalFornecedorId;
import br.com.oficina.usecase.gateway.NotaFiscalFornecedorRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaNotaFiscalFornecedorRepository implements NotaFiscalFornecedorRepository {

  private final SpringDataNotaFiscalRepository repo;

  public JpaNotaFiscalFornecedorRepository(SpringDataNotaFiscalRepository repo) {
    this.repo = repo;
  }

  @Override
  public NotaFiscalFornecedor salvar(NotaFiscalFornecedor nf) {
    NotaFiscalFornecedorIdJpa idJpa = toIdJpa(nf.getId());
    NotaFiscalFornecedorJpaEntity existing = repo.findById(idJpa).orElse(null);
    if (existing == null) {
      List<ItemNotaFiscalFornecedorJpaEntity> itens = new ArrayList<>();
      for (ItemNotaFiscalFornecedor it : nf.getItens()) {
        itens.add(
            new ItemNotaFiscalFornecedorJpaEntity(
                nf.getId().numeroNota(),
                nf.getId().serieNota(),
                nf.getId().cnpjFornecedor(),
                nf.getId().dataEmissao(),
                it.getIdSku(),
                it.getQuantidade(),
                it.getPrecoUnitario().valor()));
      }
      NotaFiscalFornecedorJpaEntity entity =
          new NotaFiscalFornecedorJpaEntity(
              nf.getId().numeroNota(),
              nf.getId().serieNota(),
              nf.getId().cnpjFornecedor(),
              nf.getId().dataEmissao(),
              nf.getNomeFornecedor(),
              nf.getValorTotal().valor(),
              nf.isEstornada(),
              nf.getCriadoEm(),
              nf.getAtualizadoEm(),
              itens);
      return toDomain(repo.save(entity));
    } else {
      existing.setEstornada(nf.isEstornada());
      existing.setAtualizadoEm(Instant.now());
      return toDomain(repo.save(existing));
    }
  }

  @Override
  public Optional<NotaFiscalFornecedor> porId(NotaFiscalFornecedorId id) {
    return repo.findById(toIdJpa(id)).map(this::toDomain);
  }

  @Override
  public boolean existe(NotaFiscalFornecedorId id) {
    return repo.existsById(toIdJpa(id));
  }

  @Override
  public List<NotaFiscalFornecedor> listarTodas() {
    return repo.findAll().stream().map(this::toDomain).toList();
  }

  private NotaFiscalFornecedorIdJpa toIdJpa(NotaFiscalFornecedorId id) {
    return new NotaFiscalFornecedorIdJpa(
        id.numeroNota(), id.serieNota(), id.cnpjFornecedor(), id.dataEmissao());
  }

  private NotaFiscalFornecedor toDomain(NotaFiscalFornecedorJpaEntity e) {
    List<ItemNotaFiscalFornecedor> itens =
        e.getItens().stream()
            .map(
                it ->
                    new ItemNotaFiscalFornecedor(
                        it.getIdSku(), it.getQuantidade(), Dinheiro.de(it.getPrecoUnitario())))
            .toList();
    NotaFiscalFornecedorId id =
        new NotaFiscalFornecedorId(
            e.getNumeroNota(), e.getSerieNota(), e.getCnpjFornecedor(), e.getDataEmissao());
    return new NotaFiscalFornecedor(
        id, e.getNomeFornecedor(), itens, e.isEstornada(), e.getCriadoEm(), e.getAtualizadoEm());
  }
}
