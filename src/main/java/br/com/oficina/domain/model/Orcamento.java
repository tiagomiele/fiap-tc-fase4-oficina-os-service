package br.com.oficina.domain.model;

import br.com.oficina.domain.enums.StatusOrcamentoItem;
import br.com.oficina.domain.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agregação lógica de itens de mesmo {@code (idOrdemServico, idOrcamento)}. Não é persistido como
 * linha separada — ele é derivado dos itens.
 */
public class Orcamento {

  private final int idOrcamento;
  private final List<ItemOrcamento> itens;

  public Orcamento(int idOrcamento, List<ItemOrcamento> itens) {
    if (idOrcamento <= 0) {
      throw new BusinessException("ORCAMENTO_INVALIDO", "id_orcamento deve ser positivo");
    }
    this.idOrcamento = idOrcamento;
    this.itens = itens == null ? new ArrayList<>() : new ArrayList<>(itens);
    validarInvariante();
  }

  private void validarInvariante() {
    if (itens.isEmpty()) return;
    StatusOrcamentoItem s = itens.get(0).getStatus();
    for (ItemOrcamento it : itens) {
      if (it.getStatus() != s) {
        throw new BusinessException(
            "ORCAMENTO_INCONSISTENTE",
            "Itens do mesmo orçamento devem compartilhar o mesmo status");
      }
    }
  }

  public int getIdOrcamento() {
    return idOrcamento;
  }

  public List<ItemOrcamento> getItens() {
    return Collections.unmodifiableList(itens);
  }

  public void adicionar(ItemOrcamento item) {
    if (status() != StatusOrcamentoItem.EM_ABERTO) {
      throw new BusinessException(
          "ORCAMENTO_FECHADO", "Orçamento não está mais aberto para novos itens");
    }
    if (item.getStatus() != StatusOrcamentoItem.EM_ABERTO) {
      throw new BusinessException("ORCAMENTO_INVALIDO", "Novo item deve estar EM_ABERTO");
    }
    itens.add(item);
  }

  public StatusOrcamentoItem status() {
    if (itens.isEmpty()) {
      return StatusOrcamentoItem.EM_ABERTO;
    }
    return itens.get(0).getStatus();
  }

  public void finalizar() {
    if (itens.isEmpty()) {
      throw new BusinessException("ORCAMENTO_VAZIO", "Orçamento sem itens não pode ser finalizado");
    }
    for (ItemOrcamento it : itens) {
      it.finalizar();
    }
  }

  public void cancelar() {
    for (ItemOrcamento it : itens) {
      it.cancelar();
    }
  }

  public Dinheiro valorTotalNaoCancelado() {
    Dinheiro soma = Dinheiro.ZERO;
    for (ItemOrcamento it : itens) {
      if (it.getStatus() != StatusOrcamentoItem.CANCELADO) {
        soma = soma.somar(it.subtotal());
      }
    }
    return soma;
  }

  public int proximoIdItem() {
    return itens.stream().mapToInt(ItemOrcamento::getIdOrcamentoItem).max().orElse(0) + 1;
  }
}
