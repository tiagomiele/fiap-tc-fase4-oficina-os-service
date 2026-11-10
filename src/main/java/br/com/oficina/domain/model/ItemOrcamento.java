package br.com.oficina.domain.model;

import br.com.oficina.domain.enums.StatusOrcamentoItem;
import br.com.oficina.domain.enums.TipoItem;
import br.com.oficina.domain.exception.BusinessException;
import java.util.Objects;

public class ItemOrcamento {

  private static final String ERRO_ITEM_ORCAMENTO_INVALIDO = "ITEM_ORCAMENTO_INVALIDO";

  private final int idOrcamento;
  private final int idOrcamentoItem;
  private final TipoItem tipoItem;
  private StatusOrcamentoItem status;
  private final Long idServicoSku;
  private final String descricao;
  private final int quantidade;
  private final Dinheiro precoUnitario;

  @SuppressWarnings("java:S107") // Reconstituição de item de orçamento requer todos os atributos.
  public ItemOrcamento(
      int idOrcamento,
      int idOrcamentoItem,
      TipoItem tipoItem,
      StatusOrcamentoItem status,
      Long idServicoSku,
      String descricao,
      int quantidade,
      Dinheiro precoUnitario) {
    if (idOrcamento <= 0) {
      throw new BusinessException(ERRO_ITEM_ORCAMENTO_INVALIDO, "id_orcamento deve ser positivo");
    }
    if (idOrcamentoItem <= 0) {
      throw new BusinessException(ERRO_ITEM_ORCAMENTO_INVALIDO, "Id do item deve ser positivo");
    }
    if (tipoItem == null) {
      throw new BusinessException(ERRO_ITEM_ORCAMENTO_INVALIDO, "Tipo do item obrigatório");
    }
    if (status == null) {
      throw new BusinessException(ERRO_ITEM_ORCAMENTO_INVALIDO, "Status obrigatório");
    }
    if (idServicoSku == null) {
      throw new BusinessException(ERRO_ITEM_ORCAMENTO_INVALIDO, "id_servico_sku obrigatório");
    }
    if (descricao == null || descricao.isBlank()) {
      throw new BusinessException(ERRO_ITEM_ORCAMENTO_INVALIDO, "Descrição obrigatória");
    }
    if (quantidade <= 0) {
      throw new BusinessException(ERRO_ITEM_ORCAMENTO_INVALIDO, "Quantidade deve ser positiva");
    }
    if (precoUnitario == null) {
      throw new BusinessException(ERRO_ITEM_ORCAMENTO_INVALIDO, "Preço unitário obrigatório");
    }
    this.idOrcamento = idOrcamento;
    this.idOrcamentoItem = idOrcamentoItem;
    this.tipoItem = tipoItem;
    this.status = status;
    this.idServicoSku = idServicoSku;
    this.descricao = descricao;
    this.quantidade = quantidade;
    this.precoUnitario = precoUnitario;
  }

  public Dinheiro subtotal() {
    return precoUnitario.multiplicar(quantidade);
  }

  public void finalizar() {
    if (status != StatusOrcamentoItem.EM_ABERTO) {
      throw new BusinessException(
          "ITEM_ORCAMENTO_INVALIDO", "Só itens EM_ABERTO podem ser finalizados");
    }
    this.status = StatusOrcamentoItem.FINALIZADO;
  }

  public void cancelar() {
    if (status == StatusOrcamentoItem.CANCELADO) {
      return;
    }
    this.status = StatusOrcamentoItem.CANCELADO;
  }

  public int getIdOrcamento() {
    return idOrcamento;
  }

  public int getIdOrcamentoItem() {
    return idOrcamentoItem;
  }

  public TipoItem getTipoItem() {
    return tipoItem;
  }

  public StatusOrcamentoItem getStatus() {
    return status;
  }

  public Long getIdServicoSku() {
    return idServicoSku;
  }

  public String getDescricao() {
    return descricao;
  }

  public int getQuantidade() {
    return quantidade;
  }

  public Dinheiro getPrecoUnitario() {
    return precoUnitario;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ItemOrcamento that)) return false;
    return idOrcamento == that.idOrcamento && idOrcamentoItem == that.idOrcamentoItem;
  }

  @Override
  public int hashCode() {
    return Objects.hash(idOrcamento, idOrcamentoItem);
  }
}
