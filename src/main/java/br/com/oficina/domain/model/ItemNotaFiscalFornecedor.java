package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;

public class ItemNotaFiscalFornecedor {

  private static final String ERRO_NF_ITEM_INVALIDO = "NF_ITEM_INVALIDO";

  private final Long idSku;
  private final int quantidade;
  private final Dinheiro precoUnitario;

  public ItemNotaFiscalFornecedor(Long idSku, int quantidade, Dinheiro precoUnitario) {
    if (idSku == null) {
      throw new BusinessException(ERRO_NF_ITEM_INVALIDO, "idSku obrigatório");
    }
    if (quantidade <= 0) {
      throw new BusinessException(ERRO_NF_ITEM_INVALIDO, "Quantidade deve ser positiva");
    }
    if (precoUnitario == null) {
      throw new BusinessException(ERRO_NF_ITEM_INVALIDO, "Preço unitário obrigatório");
    }
    this.idSku = idSku;
    this.quantidade = quantidade;
    this.precoUnitario = precoUnitario;
  }

  public Dinheiro subtotal() {
    return precoUnitario.multiplicar(quantidade);
  }

  public Long getIdSku() {
    return idSku;
  }

  public int getQuantidade() {
    return quantidade;
  }

  public Dinheiro getPrecoUnitario() {
    return precoUnitario;
  }
}
