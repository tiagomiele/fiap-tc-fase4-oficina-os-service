package br.com.oficina.adapter.persistence;

import br.com.oficina.adapter.exception.RequestIdFilter;
import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.ItemOrcamento;
import br.com.oficina.domain.model.NumeroOS;
import br.com.oficina.domain.model.OrdemServico;
import br.com.oficina.domain.model.Placa;
import br.com.oficina.usecase.gateway.HistoricoStatusOrdemServicoRepository;
import br.com.oficina.usecase.gateway.ObservabilidadeGateway;
import br.com.oficina.usecase.gateway.OrdemServicoRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class JpaOrdemServicoRepository implements OrdemServicoRepository {

  private final SpringDataOrdemServicoRepository repo;
  private final HistoricoStatusOrdemServicoRepository historico;
  private final ObservabilidadeGateway observabilidade;

  public JpaOrdemServicoRepository(
      SpringDataOrdemServicoRepository repo,
      HistoricoStatusOrdemServicoRepository historico,
      ObservabilidadeGateway observabilidade) {
    this.repo = repo;
    this.historico = historico;
    this.observabilidade = observabilidade;
  }

  @Override
  public OrdemServico salvar(OrdemServico os) {
    String id = os.getNumero().valor();
    Optional<OrdemServicoJpaEntity> existente = repo.findById(id);
    StatusOrdemServico statusAnterior =
        existente.map(OrdemServicoJpaEntity::getStatus).orElse(null);
    Instant atualizadoEmAnterior =
        existente.map(OrdemServicoJpaEntity::getAtualizadoEm).orElse(null);
    OrdemServicoJpaEntity entity = existente.orElseGet(() -> criar(id, os.getCriadoEm()));
    entity.setIdCliente(os.getIdCliente());
    entity.setIdPlaca(os.getPlaca().valor());
    entity.setStatus(os.getStatus());
    entity.setDescricaoProblema(os.getDescricaoProblema());
    entity.setValorTotalConserto(os.getValorTotalConserto().valor());
    entity.setMotivoRejeicao(os.getMotivoRejeicao());
    entity.setComprovantePagamento(os.getComprovantePagamento());
    entity.setOrcamentoAtual(os.getOrcamentoAtual());
    entity.setAtualizadoEm(os.getAtualizadoEm());
    entity.setInicioExecucao(os.getInicioExecucao());
    entity.setFimExecucao(os.getFimExecucao());

    Map<ItemOrcamentoIdJpa, ItemOrcamentoJpaEntity> existentes = new HashMap<>();
    for (ItemOrcamentoJpaEntity it : entity.getItens()) {
      existentes.put(new ItemOrcamentoIdJpa(id, it.getIdOrcamento(), it.getIdOrcamentoItem()), it);
    }
    entity.getItens().clear();
    for (ItemOrcamento it : os.getItens()) {
      ItemOrcamentoIdJpa key =
          new ItemOrcamentoIdJpa(id, it.getIdOrcamento(), it.getIdOrcamentoItem());
      ItemOrcamentoJpaEntity ij = existentes.get(key);
      if (ij == null) {
        ij = new ItemOrcamentoJpaEntity(id, it);
      } else {
        ij.atualizarDe(it);
      }
      entity.getItens().add(ij);
    }
    OrdemServicoJpaEntity saved = repo.save(entity);
    String correlationId = MDC.get(RequestIdFilter.MDC_KEY);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }
    historico.registrarTransicao(
        id, statusAnterior, os.getStatus(), os.getAtualizadoEm(), correlationId);
    if (statusAnterior == null) {
      observabilidade.ordemServicoCriada(id, os.getStatus());
    } else if (statusAnterior != os.getStatus()) {
      long duracaoMilissegundos = duracaoMilissegundos(atualizadoEmAnterior, os.getAtualizadoEm());
      observabilidade.ordemServicoStatusAlterado(
          id, statusAnterior, os.getStatus(), duracaoMilissegundos);
    }
    return toDomain(saved, os.getOrcamentoAtual());
  }

  private long duracaoMilissegundos(Instant inicio, Instant fim) {
    if (inicio == null || fim == null || fim.isBefore(inicio)) {
      return 0L;
    }
    return Duration.between(inicio, fim).toMillis();
  }

  private OrdemServicoJpaEntity criar(String id, Instant criadoEm) {
    OrdemServicoJpaEntity e = new OrdemServicoJpaEntity(id);
    e.setCriadoEm(criadoEm == null ? Instant.now() : criadoEm);
    return e;
  }

  @Override
  public Optional<OrdemServico> porNumero(NumeroOS numero) {
    return repo.findById(numero.valor()).map(e -> toDomain(e, orcamentoAtualDe(e)));
  }

  @Override
  public List<OrdemServico> listar() {
    return repo.findAll().stream().map(e -> toDomain(e, orcamentoAtualDe(e))).toList();
  }

  private int orcamentoAtualDe(OrdemServicoJpaEntity e) {
    Integer persisted = e.getOrcamentoAtual();
    return persisted == null ? 1 : persisted;
  }

  private OrdemServico toDomain(OrdemServicoJpaEntity e, int orcamentoAtual) {
    List<ItemOrcamento> itens =
        e.getItens().stream().map(ItemOrcamentoJpaEntity::toDomain).toList();
    return OrdemServico.reconstituir(
        NumeroOS.de(e.getIdOrdemServico()),
        e.getIdCliente(),
        Placa.de(e.getIdPlaca()),
        e.getStatus(),
        e.getDescricaoProblema(),
        Dinheiro.de(e.getValorTotalConserto()),
        e.getMotivoRejeicao(),
        e.getComprovantePagamento(),
        itens,
        orcamentoAtual,
        e.getCriadoEm(),
        e.getAtualizadoEm(),
        e.getInicioExecucao(),
        e.getFimExecucao());
  }
}
