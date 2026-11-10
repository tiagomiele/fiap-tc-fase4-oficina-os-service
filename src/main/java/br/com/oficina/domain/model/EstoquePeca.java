package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;

public class EstoquePeca {

  private static final String ERRO_ESTOQUE_INVALIDO = "ESTOQUE_INVALIDO";

  private final Long idSku;
  private int quantidade;
  private Instant dataHora;

  public EstoquePeca(Long idSku, int quantidade, Instant dataHora) {
    if (idSku == null) {
      throw new BusinessException(ERRO_ESTOQUE_INVALIDO, "idSku obrigatório");
    }
    if (quantidade < 0) {
      throw new BusinessException(ERRO_ESTOQUE_INVALIDO, "Quantidade não pode ser negativa");
    }
    this.idSku = idSku;
    this.quantidade = quantidade;
    this.dataHora = dataHora == null ? Instant.now() : dataHora;
  }

  public static EstoquePeca inicial(Long idSku) {
    return new EstoquePeca(idSku, 0, Instant.now());
  }

  public void entrar(int qtd) {
    if (qtd <= 0) {
      throw new BusinessException(ERRO_ESTOQUE_INVALIDO, "Quantidade deve ser positiva");
    }
    this.quantidade += qtd;
    this.dataHora = Instant.now();
  }

  public void sair(int qtd) {
    if (qtd <= 0) {
      throw new BusinessException(ERRO_ESTOQUE_INVALIDO, "Quantidade deve ser positiva");
    }
    if (quantidade < qtd) {
      throw new BusinessException(
          "ESTOQUE_INSUFICIENTE", "Estoque insuficiente para o SKU " + idSku);
    }
    this.quantidade -= qtd;
    this.dataHora = Instant.now();
  }

  public Long getIdSku() {
    return idSku;
  }

  public int getQuantidade() {
    return quantidade;
  }

  public Instant getDataHora() {
    return dataHora;
  }
}
