package br.com.oficina.domain.model;

import br.com.oficina.domain.enums.StatusOrcamentoItem;
import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.enums.TipoItem;
import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

public class OrdemServico extends RaizDeAgregado {

  private static final String ERRO_ORDEM_SERVICO_STATUS_INVALIDO = "ORDEM_SERVICO_STATUS_INVALIDO";

  private final NumeroOS numero;
  private final Long idCliente;
  private final Placa placa;
  private StatusOrdemServico status;
  private String descricaoProblema;
  private Dinheiro valorTotalConserto;
  private String motivoRejeicao;
  private String comprovantePagamento;
  private final List<ItemOrcamento> itens;
  private int orcamentoAtual;
  private final Instant criadoEm;
  private Instant atualizadoEm;
  private Instant inicioExecucao;
  private Instant fimExecucao;

  @SuppressWarnings("java:S107") // Reconstituição de agregado OS requer todos os atributos.
  private OrdemServico(
      NumeroOS numero,
      Long idCliente,
      Placa placa,
      StatusOrdemServico status,
      String descricaoProblema,
      Dinheiro valorTotalConserto,
      String motivoRejeicao,
      String comprovantePagamento,
      List<ItemOrcamento> itens,
      int orcamentoAtual,
      Instant criadoEm,
      Instant atualizadoEm,
      Instant inicioExecucao,
      Instant fimExecucao) {
    this.numero = Objects.requireNonNull(numero);
    this.idCliente = Objects.requireNonNull(idCliente);
    this.placa = Objects.requireNonNull(placa);
    this.status = Objects.requireNonNull(status);
    this.descricaoProblema = descricaoProblema;
    this.valorTotalConserto = valorTotalConserto == null ? Dinheiro.ZERO : valorTotalConserto;
    this.motivoRejeicao = motivoRejeicao;
    this.comprovantePagamento = comprovantePagamento;
    this.itens = itens == null ? new ArrayList<>() : new ArrayList<>(itens);
    this.orcamentoAtual = orcamentoAtual <= 0 ? 1 : orcamentoAtual;
    this.criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    this.atualizadoEm = atualizadoEm == null ? this.criadoEm : atualizadoEm;
    this.inicioExecucao = inicioExecucao;
    this.fimExecucao = fimExecucao;
  }

  public static OrdemServico abrir(
      NumeroOS numero, Long idCliente, Placa placa, String descricaoProblema) {
    Instant agora = Instant.now();
    return new OrdemServico(
        numero,
        idCliente,
        placa,
        StatusOrdemServico.RECEBIDA,
        descricaoProblema,
        Dinheiro.ZERO,
        null,
        null,
        new ArrayList<>(),
        1,
        agora,
        agora,
        null,
        null);
  }

  @SuppressWarnings("java:S107") // Reconstituição de agregado OS requer todos os atributos.
  public static OrdemServico reconstituir(
      NumeroOS numero,
      Long idCliente,
      Placa placa,
      StatusOrdemServico status,
      String descricaoProblema,
      Dinheiro valorTotalConserto,
      String motivoRejeicao,
      String comprovantePagamento,
      List<ItemOrcamento> itens,
      int orcamentoAtual,
      Instant criadoEm,
      Instant atualizadoEm,
      Instant inicioExecucao,
      Instant fimExecucao) {
    return new OrdemServico(
        numero,
        idCliente,
        placa,
        status,
        descricaoProblema,
        valorTotalConserto,
        motivoRejeicao,
        comprovantePagamento,
        itens,
        orcamentoAtual,
        criadoEm,
        atualizadoEm,
        inicioExecucao,
        fimExecucao);
  }

  public Orcamento orcamento(int id) {
    List<ItemOrcamento> list = itens.stream().filter(i -> i.getIdOrcamento() == id).toList();
    return new Orcamento(id, list);
  }

  public Orcamento orcamentoAtual() {
    return orcamento(orcamentoAtual);
  }

  public ItemOrcamento adicionarServico(
      Long idServico, String descricao, int quantidade, Dinheiro precoUnitario) {
    return adicionarItem(TipoItem.SERVICO, idServico, descricao, quantidade, precoUnitario);
  }

  public ItemOrcamento adicionarPeca(
      Long idSku, String descricao, int quantidade, Dinheiro precoUnitario) {
    return adicionarItem(TipoItem.PECA, idSku, descricao, quantidade, precoUnitario);
  }

  private ItemOrcamento adicionarItem(
      TipoItem tipo, Long idServicoSku, String descricao, int quantidade, Dinheiro preco) {
    if (status != StatusOrdemServico.RECEBIDA && status != StatusOrdemServico.EM_DIAGNOSTICO) {
      throw new BusinessException(
          ERRO_ORDEM_SERVICO_STATUS_INVALIDO,
          "Itens só podem ser adicionados em RECEBIDA ou EM_DIAGNOSTICO");
    }
    if (status == StatusOrdemServico.RECEBIDA) {
      status = StatusOrdemServico.EM_DIAGNOSTICO;
    }
    int proximoId = proximoIdItemAtual();
    ItemOrcamento item =
        new ItemOrcamento(
            orcamentoAtual,
            proximoId,
            tipo,
            StatusOrcamentoItem.EM_ABERTO,
            idServicoSku,
            descricao,
            quantidade,
            preco);
    itens.add(item);
    recalcularValorTotal();
    atualizadoEm = Instant.now();
    return item;
  }

  private int proximoIdItemAtual() {
    return itens.stream()
            .filter(i -> i.getIdOrcamento() == orcamentoAtual)
            .mapToInt(ItemOrcamento::getIdOrcamentoItem)
            .max()
            .orElse(0)
        + 1;
  }

  public void enviarParaAprovacao() {
    if (status != StatusOrdemServico.EM_DIAGNOSTICO) {
      throw new BusinessException(
          ERRO_ORDEM_SERVICO_STATUS_INVALIDO,
          "OS só pode ser enviada para aprovação quando estiver em EM_DIAGNOSTICO");
    }
    Orcamento atual = orcamentoAtual();
    if (atual.getItens().isEmpty()) {
      throw new BusinessException(
          "ORCAMENTO_VAZIO", "Orçamento atual está vazio. Adicione itens antes de enviar.");
    }
    atual.finalizar();
    status = StatusOrdemServico.AGUARDANDO_APROVACAO;
    recalcularValorTotal();
    atualizadoEm = Instant.now();
  }

  public void aprovar() {
    exigirStatus(StatusOrdemServico.AGUARDANDO_APROVACAO);
    status = StatusOrdemServico.EM_EXECUCAO;
    Instant agora = Instant.now();
    if (inicioExecucao == null) {
      inicioExecucao = agora;
    }
    atualizadoEm = agora;
  }

  public List<ItemOrcamento> rejeitarCancelar(String motivo) {
    exigirStatus(StatusOrdemServico.AGUARDANDO_APROVACAO);
    List<ItemOrcamento> doOrcAtual = itensDoOrcamentoAtualCopia();
    orcamentoAtual().cancelar();
    status = StatusOrdemServico.CANCELADA;
    motivoRejeicao = motivo;
    recalcularValorTotal();
    atualizadoEm = Instant.now();
    return doOrcAtual;
  }

  public List<ItemOrcamento> rejeitarRefazer(String motivo) {
    exigirStatus(StatusOrdemServico.AGUARDANDO_APROVACAO);
    List<ItemOrcamento> doOrcAtual = itensDoOrcamentoAtualCopia();
    orcamentoAtual().cancelar();
    orcamentoAtual += 1;
    status = StatusOrdemServico.EM_DIAGNOSTICO;
    motivoRejeicao = motivo;
    recalcularValorTotal();
    atualizadoEm = Instant.now();
    return doOrcAtual;
  }

  public void concluirReparo() {
    exigirStatus(StatusOrdemServico.EM_EXECUCAO);
    status = StatusOrdemServico.AGUARDANDO_PAGAMENTO;
    Instant agora = Instant.now();
    if (fimExecucao == null) {
      fimExecucao = agora;
    }
    atualizadoEm = agora;
  }

  public void confirmarPagamento(String comprovante) {
    exigirStatus(StatusOrdemServico.AGUARDANDO_PAGAMENTO);
    if (comprovante == null || comprovante.isBlank()) {
      throw new BusinessException("COMPROVANTE_INVALIDO", "Comprovante obrigatório");
    }
    this.comprovantePagamento = comprovante;
    status = StatusOrdemServico.PAGA;
    atualizadoEm = Instant.now();
  }

  /**
   * Entrega o veículo ao cliente.
   *
   * <p>Permitido a partir de:
   *
   * <ul>
   *   <li>{@link StatusOrdemServico#PAGA} — fluxo normal após confirmação de pagamento.
   *   <li>{@link StatusOrdemServico#CANCELADA} — OS cancelada (rejeição do orçamento); o veículo
   *       volta para o cliente sem cobrança adicional.
   * </ul>
   */
  public void entregar() {
    if (status != StatusOrdemServico.PAGA && status != StatusOrdemServico.CANCELADA) {
      throw new BusinessException(
          ERRO_ORDEM_SERVICO_STATUS_INVALIDO,
          "OS em status " + status + "; esperado PAGA ou CANCELADA");
    }
    status = StatusOrdemServico.ENTREGUE;
    atualizadoEm = Instant.now();
  }

  /**
   * Cancela uma OS que está em {@link StatusOrdemServico#EM_DIAGNOSTICO}. Cancela todos os
   * orçamentos vinculados (todos os itens passam a {@link StatusOrcamentoItem#CANCELADO}) e move a
   * OS para {@link StatusOrdemServico#CANCELADA}. Retorna os itens que ainda não estavam cancelados
   * (para devolução das peças ao estoque pelo caso de uso).
   */
  public List<ItemOrcamento> cancelarEmDiagnostico(String motivo) {
    exigirStatus(StatusOrdemServico.EM_DIAGNOSTICO);
    List<ItemOrcamento> aDevolver =
        itens.stream().filter(i -> i.getStatus() != StatusOrcamentoItem.CANCELADO).toList();
    for (ItemOrcamento it : itens) {
      it.cancelar();
    }
    status = StatusOrdemServico.CANCELADA;
    motivoRejeicao = motivo;
    recalcularValorTotal();
    atualizadoEm = Instant.now();
    return aDevolver;
  }

  /**
   * Alteração genérica de status (função de contingência do perfil administrativo). Valida o status
   * de destino conforme as regras de negócio. Não executa efeitos colaterais financeiros ou de
   * estoque — apenas a transição de status e o registro dos marcos de execução quando aplicável.
   */
  public void alterarStatus(StatusOrdemServico novo) {
    Objects.requireNonNull(novo, "Novo status é obrigatório");
    validarDestinoContingencia(novo);
    this.status = novo;
    Instant agora = Instant.now();
    if (novo == StatusOrdemServico.EM_EXECUCAO && inicioExecucao == null) {
      inicioExecucao = agora;
    }
    if (novo == StatusOrdemServico.AGUARDANDO_PAGAMENTO && fimExecucao == null) {
      fimExecucao = agora;
    }
    atualizadoEm = agora;
  }

  private void validarDestinoContingencia(StatusOrdemServico novo) {
    switch (novo) {
      case RECEBIDA ->
          exigirRegra(itens.isEmpty(), "RECEBIDA só é permitido quando não há orçamento vinculado");
      case EM_DIAGNOSTICO, AGUARDANDO_APROVACAO -> {
        // Permitido sem condição adicional.
      }
      case EM_EXECUCAO ->
          exigirRegra(
              possuiItemVinculado(),
              "EM_EXECUCAO exige ao menos um serviço ou peça vinculado à OS");
      case AGUARDANDO_PAGAMENTO, ENTREGUE ->
          exigirRegra(ultimoOrcamentoEncerrado(), "Operação exige o último orçamento encerrado");
      case CANCELADA ->
          exigirRegra(
              todosOrcamentosCancelados(),
              "CANCELADA exige que todos os orçamentos vinculados estejam cancelados");
      case PAGA ->
          throw new BusinessException(
              ERRO_ORDEM_SERVICO_STATUS_INVALIDO,
              "Transição para PAGA deve usar o fluxo de confirmação de pagamento");
    }
  }

  private void exigirRegra(boolean condicao, String mensagem) {
    if (!condicao) {
      throw new BusinessException(ERRO_ORDEM_SERVICO_STATUS_INVALIDO, mensagem);
    }
  }

  private boolean possuiItemVinculado() {
    return itens.stream().anyMatch(i -> i.getStatus() != StatusOrcamentoItem.CANCELADO);
  }

  private boolean ultimoOrcamentoEncerrado() {
    Orcamento atual = orcamentoAtual();
    return !atual.getItens().isEmpty() && atual.status() != StatusOrcamentoItem.EM_ABERTO;
  }

  private boolean todosOrcamentosCancelados() {
    return !itens.isEmpty()
        && itens.stream().allMatch(i -> i.getStatus() == StatusOrcamentoItem.CANCELADO);
  }

  private void exigirStatus(StatusOrdemServico esperado) {
    if (status != esperado) {
      throw new BusinessException(
          ERRO_ORDEM_SERVICO_STATUS_INVALIDO, "OS em status " + status + "; esperado " + esperado);
    }
  }

  private void recalcularValorTotal() {
    Dinheiro soma = Dinheiro.ZERO;
    for (ItemOrcamento it : itens) {
      if (it.getStatus() != StatusOrcamentoItem.CANCELADO) {
        soma = soma.somar(it.subtotal());
      }
    }
    this.valorTotalConserto = soma;
  }

  private List<ItemOrcamento> itensDoOrcamentoAtualCopia() {
    return itens.stream().filter(i -> i.getIdOrcamento() == orcamentoAtual).toList();
  }

  /** Retorna mapa de orçamentos (chave = id_orcamento, valor = lista imutável de itens). */
  public java.util.SortedMap<Integer, List<ItemOrcamento>> mapaOrcamentos() {
    java.util.SortedMap<Integer, List<ItemOrcamento>> out = new TreeMap<>();
    for (ItemOrcamento it : itens) {
      out.computeIfAbsent(it.getIdOrcamento(), k -> new ArrayList<>()).add(it);
    }
    return out;
  }

  public NumeroOS getNumero() {
    return numero;
  }

  public Long getIdCliente() {
    return idCliente;
  }

  public Placa getPlaca() {
    return placa;
  }

  public StatusOrdemServico getStatus() {
    return status;
  }

  public String getDescricaoProblema() {
    return descricaoProblema;
  }

  public Dinheiro getValorTotalConserto() {
    return valorTotalConserto;
  }

  public String getMotivoRejeicao() {
    return motivoRejeicao;
  }

  public String getComprovantePagamento() {
    return comprovantePagamento;
  }

  public List<ItemOrcamento> getItens() {
    return Collections.unmodifiableList(itens);
  }

  public int getOrcamentoAtual() {
    return orcamentoAtual;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public Instant getAtualizadoEm() {
    return atualizadoEm;
  }

  /**
   * Timestamp em que a OS entrou pela primeira vez no status {@link StatusOrdemServico#EM_EXECUCAO}
   * (primeira aprovação de orçamento). Nunca sobrescrito.
   */
  public Instant getInicioExecucao() {
    return inicioExecucao;
  }

  /**
   * Timestamp em que a OS saiu pela primeira vez de {@link StatusOrdemServico#EM_EXECUCAO} para
   * {@link StatusOrdemServico#AGUARDANDO_PAGAMENTO}. Nunca sobrescrito.
   */
  public Instant getFimExecucao() {
    return fimExecucao;
  }
}
