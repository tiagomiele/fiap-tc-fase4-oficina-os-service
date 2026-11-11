package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.enums.StatusOrcamentoItem;
import br.com.oficina.domain.enums.TipoItem;
import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.ItemOrcamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "orcamentos_itens_ordem_servico")
@IdClass(ItemOrcamentoIdJpa.class)
public class ItemOrcamentoJpaEntity {

  @Id
  @Column(name = "id_ordem_servico", length = 20, nullable = false)
  private String idOrdemServico;

  @Id
  @Column(name = "id_orcamento", nullable = false)
  private Integer idOrcamento;

  @Id
  @Column(name = "id_orcamento_item", nullable = false)
  private Integer idOrcamentoItem;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_item", nullable = false, length = 16)
  private TipoItem tipoItem;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private StatusOrcamentoItem status;

  @Column(name = "id_servico_sku", nullable = false)
  private Long idServicoSku;

  @Column(nullable = false)
  private String descricao;

  @Column(nullable = false)
  private int quantidade;

  @Column(name = "preco_unitario", nullable = false, precision = 12, scale = 2)
  private BigDecimal precoUnitario;

  protected ItemOrcamentoJpaEntity() {}

  public ItemOrcamentoJpaEntity(String idOrdemServico, ItemOrcamento d) {
    this.idOrdemServico = idOrdemServico;
    atualizarDe(d);
  }

  public void atualizarDe(ItemOrcamento d) {
    this.idOrcamento = d.getIdOrcamento();
    this.idOrcamentoItem = d.getIdOrcamentoItem();
    this.tipoItem = d.getTipoItem();
    this.status = d.getStatus();
    this.idServicoSku = d.getIdServicoSku();
    this.descricao = d.getDescricao();
    this.quantidade = d.getQuantidade();
    this.precoUnitario = d.getPrecoUnitario().valor();
  }

  public ItemOrcamento toDomain() {
    return new ItemOrcamento(
        idOrcamento,
        idOrcamentoItem,
        tipoItem,
        status,
        idServicoSku,
        descricao,
        quantidade,
        Dinheiro.de(precoUnitario));
  }

  public String getIdOrdemServico() {
    return idOrdemServico;
  }

  public Integer getIdOrcamento() {
    return idOrcamento;
  }

  public Integer getIdOrcamentoItem() {
    return idOrcamentoItem;
  }
}
