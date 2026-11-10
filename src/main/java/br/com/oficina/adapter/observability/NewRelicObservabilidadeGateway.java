package br.com.oficina.adapter.observability;

import br.com.oficina.adapter.exception.RequestIdFilter;
import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.usecase.gateway.ObservabilidadeGateway;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.TraceMetadata;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class NewRelicObservabilidadeGateway implements ObservabilidadeGateway {

  private static final Logger log = LoggerFactory.getLogger(NewRelicObservabilidadeGateway.class);

  private final boolean enabled;
  private final String environment;

  public NewRelicObservabilidadeGateway(
      @Value("${oficina.observability.new-relic.enabled:false}") boolean enabled,
      @Value("${oficina.environment:local}") String environment) {
    this.enabled = enabled;
    this.environment = environment;
  }

  @Override
  public void ordemServicoCriada(String numeroOs, StatusOrdemServico status) {
    Map<String, Object> atributos = atributosBase(numeroOs);
    atributos.put("status", status.name());
    registrar("OrdemServicoCriada", atributos);
  }

  @Override
  public void ordemServicoStatusAlterado(
      String numeroOs,
      StatusOrdemServico statusAnterior,
      StatusOrdemServico novoStatus,
      long duracaoMilissegundos) {
    Map<String, Object> atributos = atributosBase(numeroOs);
    atributos.put("statusAnterior", statusAnterior.name());
    atributos.put("novoStatus", novoStatus.name());
    atributos.put("duracaoMilissegundos", duracaoMilissegundos);
    registrar("OrdemServicoStatusAlterado", atributos);
  }

  @Override
  public void ordemServicoProcessamentoFalhou(String numeroOs, String operacao, String codigoErro) {
    Map<String, Object> atributos = atributosBase(numeroOs);
    atributos.put("operacao", operacao);
    atributos.put("codigoErro", codigoErro);
    registrar("OrdemServicoProcessamentoFalhou", atributos);
  }

  @Override
  public void integracaoExternaFalhou(String integracao, String operacao, String codigoErro) {
    Map<String, Object> atributos = atributosBase(null);
    atributos.put("integracao", integracao);
    atributos.put("operacao", operacao);
    atributos.put("codigoErro", codigoErro);
    registrar("IntegracaoExternaFalhou", atributos);
  }

  private Map<String, Object> atributosBase(String numeroOs) {
    Map<String, Object> atributos = new HashMap<>();
    atributos.put("environment", environment);
    if (numeroOs != null && !numeroOs.isBlank()) {
      atributos.put("numeroOs", numeroOs);
    }
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    if (requestId != null && !requestId.isBlank()) {
      atributos.put("requestId", requestId);
    }
    if (enabled) {
      TraceMetadata traceMetadata = NewRelic.getAgent().getTraceMetadata();
      if (!traceMetadata.getTraceId().isBlank()) {
        atributos.put("traceId", traceMetadata.getTraceId());
      }
      if (!traceMetadata.getSpanId().isBlank()) {
        atributos.put("spanId", traceMetadata.getSpanId());
      }
    }
    return atributos;
  }

  private void registrar(String tipo, Map<String, Object> atributos) {
    if (!enabled) {
      return;
    }
    Map<String, Object> atributosImutaveis = Map.copyOf(atributos);
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              registrarAgora(tipo, atributosImutaveis);
            }
          });
      return;
    }
    registrarAgora(tipo, atributosImutaveis);
  }

  private void registrarAgora(String tipo, Map<String, Object> atributos) {
    try {
      NewRelic.getAgent().getInsights().recordCustomEvent(tipo, atributos);
    } catch (RuntimeException exception) {
      log.warn("Falha ao registrar evento de observabilidade: tipo={}", tipo);
    }
  }
}
