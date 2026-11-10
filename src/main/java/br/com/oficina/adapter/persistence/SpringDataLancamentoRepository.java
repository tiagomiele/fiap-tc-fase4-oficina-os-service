package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.enums.TipoLancamento;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataLancamentoRepository
    extends JpaRepository<LancamentoFinanceiroJpaEntity, Long> {

  List<LancamentoFinanceiroJpaEntity> findByTipoOrderByDataLancamentoDesc(TipoLancamento tipo);

  Optional<LancamentoFinanceiroJpaEntity>
      findByNumeroNotaAndSerieNotaAndCnpjFornecedorAndDataEmissao(
          String numero, String serie, String cnpj, LocalDate dataEmissao);
}
