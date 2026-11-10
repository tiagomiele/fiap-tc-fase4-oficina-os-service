package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.model.HistoricoStatusOrdemServico;
import java.time.Instant;
import java.util.List;

public interface HistoricoStatusOrdemServicoRepository {

  void registrarTransicao(
      String numeroOs,
      StatusOrdemServico statusAnterior,
      StatusOrdemServico novoStatus,
      Instant instante,
      String correlationId);

  List<HistoricoStatusOrdemServico> porOrdemServico(String numeroOs);
}
