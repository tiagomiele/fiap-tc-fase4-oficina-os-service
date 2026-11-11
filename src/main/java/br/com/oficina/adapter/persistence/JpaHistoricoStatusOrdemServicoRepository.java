package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.model.HistoricoStatusOrdemServico;
import br.com.oficina.usecase.gateway.HistoricoStatusOrdemServicoRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JpaHistoricoStatusOrdemServicoRepository
    implements HistoricoStatusOrdemServicoRepository {

  private final SpringDataHistoricoStatusOrdemServicoRepository repository;

  public JpaHistoricoStatusOrdemServicoRepository(
      SpringDataHistoricoStatusOrdemServicoRepository repository) {
    this.repository = repository;
  }

  @Override
  public void registrarTransicao(
      String numeroOs,
      StatusOrdemServico statusAnterior,
      StatusOrdemServico novoStatus,
      Instant instante,
      String correlationId) {
    if (statusAnterior == novoStatus) {
      return;
    }
    repository
        .findFirstByIdOrdemServicoAndSaidaEmIsNullOrderByEntradaEmDesc(numeroOs)
        .ifPresent(
            atual -> {
              atual.encerrar(instante);
              repository.saveAndFlush(atual);
            });
    repository.save(
        new HistoricoStatusOrdemServicoJpaEntity(numeroOs, novoStatus, instante, correlationId));
  }

  @Override
  public List<HistoricoStatusOrdemServico> porOrdemServico(String numeroOs) {
    return repository.findByIdOrdemServicoOrderByEntradaEmAsc(numeroOs).stream()
        .map(HistoricoStatusOrdemServicoJpaEntity::toDomain)
        .toList();
  }
}
