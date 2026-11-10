package br.com.oficina.adapter.persistence;

import java.io.Serializable;
import java.util.Objects;

public class ItemOrcamentoIdJpa implements Serializable {

  private String idOrdemServico;
  private Integer idOrcamento;
  private Integer idOrcamentoItem;

  public ItemOrcamentoIdJpa() {}

  public ItemOrcamentoIdJpa(String idOrdemServico, Integer idOrcamento, Integer idOrcamentoItem) {
    this.idOrdemServico = idOrdemServico;
    this.idOrcamento = idOrcamento;
    this.idOrcamentoItem = idOrcamentoItem;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ItemOrcamentoIdJpa other)) return false;
    return Objects.equals(idOrdemServico, other.idOrdemServico)
        && Objects.equals(idOrcamento, other.idOrcamento)
        && Objects.equals(idOrcamentoItem, other.idOrcamentoItem);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idOrdemServico, idOrcamento, idOrcamentoItem);
  }
}
