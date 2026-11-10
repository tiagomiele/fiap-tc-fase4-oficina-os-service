package br.com.oficina.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataHistoricoStatusOrdemServicoRepository
    extends JpaRepository<HistoricoStatusOrdemServicoJpaEntity, Long> {

  Optional<HistoricoStatusOrdemServicoJpaEntity>
      findFirstByIdOrdemServicoAndSaidaEmIsNullOrderByEntradaEmDesc(String idOrdemServico);

  List<HistoricoStatusOrdemServicoJpaEntity> findByIdOrdemServicoOrderByEntradaEmAsc(
      String idOrdemServico);
}
