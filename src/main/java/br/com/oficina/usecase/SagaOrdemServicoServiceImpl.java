package br.com.oficina.usecase;

import static br.com.oficina.usecase.saga.MensagemSaga.dados;

import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.model.ItemOrcamento;
import br.com.oficina.domain.model.OrdemServico;
import br.com.oficina.usecase.gateway.MensagemProcessadaRepository;
import br.com.oficina.usecase.gateway.MensageriaGateway;
import br.com.oficina.usecase.saga.MensagemSaga;
import br.com.oficina.usecase.saga.TipoMensagemSaga;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestrador da Saga da OS. O estado da Saga é o próprio status da OS: cada evento só é aplicado
 * quando a OS está no status esperado, e cada passo envia o próximo comando ou a compensação.
 */
@Service
public class SagaOrdemServicoServiceImpl {

  static final String MOTIVO_ESTOQUE_INSUFICIENTE =
      "Estoque insuficiente para as peças do orçamento aprovado";
  static final String MOTIVO_REJEICAO_PADRAO = "Orçamento rejeitado pelo cliente";

  private static final Logger log = LoggerFactory.getLogger(SagaOrdemServicoServiceImpl.class);

  private final OrdemServicoServiceImpl ordens;
  private final MensageriaGateway mensageria;
  private final MensagemProcessadaRepository processadas;

  public SagaOrdemServicoServiceImpl(
      OrdemServicoServiceImpl ordens,
      MensageriaGateway mensageria,
      MensagemProcessadaRepository processadas) {
    this.ordens = ordens;
    this.mensageria = mensageria;
    this.processadas = processadas;
  }

  /**
   * Aplica um evento recebido. Retorna {@code false} quando o evento foi ignorado (duplicado, fora
   * de ordem ou não destinado ao OS Service). Erros sobem para que a mensagem volte à fila e, após
   * as tentativas, vá para a DLQ.
   */
  @Transactional
  public boolean processar(MensagemSaga evento) {
    TipoMensagemSaga tipo = evento.tipo();
    if (!tipo.isEvento()) {
      log.warn("Mensagem {} ignorada: {} é um comando, não um evento", evento.messageId(), tipo);
      return false;
    }
    if (!processadas.registrarSeNova(evento)) {
      log.info("Mensagem {} ({}) já processada; ignorada", evento.messageId(), tipo);
      return false;
    }
    String numero = evento.numeroOs();
    OrdemServico os = ordens.consultar(numero);
    if (os.getStatus() != tipo.statusEsperado()) {
      log.warn(
          "Evento {} da OS {} ignorado: status atual {} (esperado {})",
          tipo,
          numero,
          os.getStatus(),
          tipo.statusEsperado());
      return false;
    }
    log.info("Saga OS {}: aplicando evento {}", numero, tipo);
    switch (tipo) {
      case DIAGNOSTICO_INICIADO -> ordens.alterarStatus(numero, StatusOrdemServico.EM_DIAGNOSTICO);
      case DIAGNOSTICO_CONCLUIDO -> solicitarOrcamento(ordens.enviarParaAprovacao(numero), evento);
      case ORCAMENTO_APROVADO -> aprovarOrcamento(numero, evento);
      case ORCAMENTO_REJEITADO -> rejeitarOrcamento(numero, evento);
      case REPARO_CONCLUIDO -> solicitarPagamento(ordens.concluirReparo(numero), evento);
      case PAGAMENTO_CONFIRMADO -> ordens.confirmarPagamento(numero, comprovante(evento));
      default -> ordens.notificarPagamentoRecusado(numero, evento.texto("motivo"));
    }
    return true;
  }

  private void solicitarOrcamento(OrdemServico os, MensagemSaga evento) {
    List<Map<String, Object>> itens =
        os.orcamentoAtual().getItens().stream().map(SagaOrdemServicoServiceImpl::item).toList();
    enviar(
        TipoMensagemSaga.GERAR_ORCAMENTO,
        os,
        dados(
            "idCliente", os.getIdCliente(),
            "valorTotal", os.orcamentoAtual().valorTotalNaoCancelado().valor(),
            "itens", itens),
        evento);
  }

  private void aprovarOrcamento(String numero, MensagemSaga evento) {
    if (ordens.estoqueSuficiente(numero)) {
      enviar(TipoMensagemSaga.INICIAR_REPARO, ordens.aprovar(numero), Map.of(), evento);
      return;
    }
    log.warn("Saga OS {}: compensação por estoque insuficiente", numero);
    OrdemServico os = ordens.rejeitarCancelar(numero, MOTIVO_ESTOQUE_INSUFICIENTE);
    Map<String, Object> motivo = dados("motivo", MOTIVO_ESTOQUE_INSUFICIENTE);
    enviar(TipoMensagemSaga.CANCELAR_ORCAMENTO, os, motivo, evento);
    enviar(TipoMensagemSaga.CANCELAR_EXECUCAO, os, motivo, evento);
  }

  private void rejeitarOrcamento(String numero, MensagemSaga evento) {
    String motivo = evento.texto("motivo");
    if (motivo == null || motivo.isBlank()) {
      motivo = MOTIVO_REJEICAO_PADRAO;
    }
    if ("REFAZER".equalsIgnoreCase(evento.texto("acao"))) {
      OrdemServico os = ordens.rejeitarRefazer(numero, motivo);
      enviar(
          TipoMensagemSaga.INICIAR_DIAGNOSTICO,
          os,
          dados("refazer", true, "motivo", motivo),
          evento);
      return;
    }
    log.warn("Saga OS {}: compensação por orçamento rejeitado com cancelamento", numero);
    OrdemServico os = ordens.rejeitarCancelar(numero, motivo);
    enviar(TipoMensagemSaga.CANCELAR_EXECUCAO, os, dados("motivo", motivo), evento);
  }

  private void solicitarPagamento(OrdemServico os, MensagemSaga evento) {
    enviar(
        TipoMensagemSaga.SOLICITAR_PAGAMENTO,
        os,
        dados("idCliente", os.getIdCliente(), "valor", os.getValorTotalConserto().valor()),
        evento);
  }

  /** O comando herda o correlationId do evento que o originou. */
  private void enviar(
      TipoMensagemSaga tipo, OrdemServico os, Map<String, Object> payload, MensagemSaga evento) {
    mensageria.enviar(
        MensagemSaga.comando(tipo, os.getNumero().valor(), payload)
            .comCorrelationId(evento.correlationId()));
  }

  private static String comprovante(MensagemSaga evento) {
    String comprovante = evento.texto("comprovante");
    if (comprovante == null || comprovante.isBlank()) {
      comprovante = evento.texto("pagamentoId");
    }
    return comprovante == null || comprovante.isBlank() ? evento.messageId() : comprovante;
  }

  private static Map<String, Object> item(ItemOrcamento item) {
    return dados(
        "tipo", item.getTipoItem().name(),
        "idServicoSku", item.getIdServicoSku(),
        "descricao", item.getDescricao(),
        "quantidade", item.getQuantidade(),
        "precoUnitario", item.getPrecoUnitario().valor());
  }
}
