package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.EstoquePeca;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "estoque_pecas")
public class EstoquePecaJpaEntity {

  @Id
  @Column(name = "id_sku")
  private Long idSku;

  @Column(nullable = false)
  private int quantidade;

  @Column(name = "data_hora", nullable = false)
  private Instant dataHora;

  protected EstoquePecaJpaEntity() {}

  public EstoquePecaJpaEntity(EstoquePeca e) {
    atualizarDe(e);
  }

  public void atualizarDe(EstoquePeca e) {
    this.idSku = e.getIdSku();
    this.quantidade = e.getQuantidade();
    this.dataHora = e.getDataHora();
  }

  public EstoquePeca toDomain() {
    return new EstoquePeca(idSku, quantidade, dataHora);
  }

  public Long getIdSku() {
    return idSku;
  }
}
