package br.com.oficina.adapter.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.model.NumeroOS;
import br.com.oficina.domain.model.OrdemServico;
import br.com.oficina.domain.model.Placa;
import br.com.oficina.usecase.gateway.HistoricoStatusOrdemServicoRepository;
import br.com.oficina.usecase.gateway.ObservabilidadeGateway;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JpaOrdemServicoRepositoryObservabilidadeTest {

  private static final String NUMERO_OS = "OS-082026-000001";

  private final SpringDataOrdemServicoRepository springData =
      mock(SpringDataOrdemServicoRepository.class);
  private final HistoricoStatusOrdemServicoRepository historico =
      mock(HistoricoStatusOrdemServicoRepository.class);
  private final ObservabilidadeGateway observabilidade = mock(ObservabilidadeGateway.class);
  private final JpaOrdemServicoRepository repository =
      new JpaOrdemServicoRepository(springData, historico, observabilidade);

  @Test
  void registraEventoQuandoOrdemServicoEhCriada() {
    OrdemServico ordemServico =
        OrdemServico.abrir(NumeroOS.de(NUMERO_OS), 1L, Placa.de("ABC1D23"), "Ruido tecnico");
    when(springData.findById(NUMERO_OS)).thenReturn(Optional.empty());
    when(springData.save(any(OrdemServicoJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    repository.salvar(ordemServico);

    verify(observabilidade).ordemServicoCriada(NUMERO_OS, StatusOrdemServico.RECEBIDA);
  }

  @Test
  void registraEventoQuandoStatusEhAlterado() {
    Instant instanteAnterior = Instant.parse("2026-08-10T10:00:00Z");
    OrdemServicoJpaEntity existente = entidadeExistente(instanteAnterior);
    OrdemServico ordemServico =
        OrdemServico.reconstituir(
            NumeroOS.de(NUMERO_OS),
            1L,
            Placa.de("ABC1D23"),
            StatusOrdemServico.RECEBIDA,
            "Ruido tecnico",
            null,
            null,
            null,
            java.util.List.of(),
            1,
            instanteAnterior,
            instanteAnterior,
            null,
            null);
    ordemServico.alterarStatus(StatusOrdemServico.EM_DIAGNOSTICO);
    when(springData.findById(NUMERO_OS)).thenReturn(Optional.of(existente));
    when(springData.save(any(OrdemServicoJpaEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    repository.salvar(ordemServico);

    verify(observabilidade)
        .ordemServicoStatusAlterado(
            org.mockito.ArgumentMatchers.eq(NUMERO_OS),
            org.mockito.ArgumentMatchers.eq(StatusOrdemServico.RECEBIDA),
            org.mockito.ArgumentMatchers.eq(StatusOrdemServico.EM_DIAGNOSTICO),
            org.mockito.ArgumentMatchers.longThat(valor -> valor >= 0));
  }

  private OrdemServicoJpaEntity entidadeExistente(Instant instante) {
    OrdemServicoJpaEntity entidade = new OrdemServicoJpaEntity(NUMERO_OS);
    entidade.setIdCliente(1L);
    entidade.setIdPlaca("ABC1D23");
    entidade.setStatus(StatusOrdemServico.RECEBIDA);
    entidade.setDescricaoProblema("Ruido tecnico");
    entidade.setValorTotalConserto(java.math.BigDecimal.ZERO);
    entidade.setOrcamentoAtual(1);
    entidade.setCriadoEm(instante);
    entidade.setAtualizadoEm(instante);
    return entidade;
  }
}
