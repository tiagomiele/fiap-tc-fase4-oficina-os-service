package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.enums.StatusOrdemServico;

public interface ObservabilidadeGateway {

  void ordemServicoCriada(String numeroOs, StatusOrdemServico status);

  void ordemServicoStatusAlterado(
      String numeroOs,
      StatusOrdemServico statusAnterior,
      StatusOrdemServico novoStatus,
      long duracaoMilissegundos);

  void ordemServicoProcessamentoFalhou(String numeroOs, String operacao, String codigoErro);

  void integracaoExternaFalhou(String integracao, String operacao, String codigoErro);
}
