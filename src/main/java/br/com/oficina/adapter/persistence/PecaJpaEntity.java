package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.Peca;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pecas")
public class PecaJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_sku")
  private Long idSku;

  @Column(nullable = false)
  private String nome;

  @Column(name = "preco_venda", nullable = false, precision = 12, scale = 2)
  private BigDecimal precoVenda;

  @Column(nullable = false)
  private boolean ativo;

  @Version
  @Column(nullable = false)
  private Long versao;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private Instant atualizadoEm;

  protected PecaJpaEntity() {}

  public PecaJpaEntity(Peca p) {
    atualizarDe(p);
    this.criadoEm = p.getCriadoEm();
  }

  public void atualizarDe(Peca p) {
    this.idSku = p.getIdSku();
    this.nome = p.getNome();
    this.precoVenda = p.getPrecoVenda().valor();
    this.ativo = p.isAtivo();
    this.atualizadoEm = p.getAtualizadoEm();
  }

  public Peca toDomain() {
    return new Peca(
        idSku,
        nome,
        Dinheiro.de(precoVenda),
        ativo,
        versao == null ? 0L : versao,
        criadoEm,
        atualizadoEm);
  }

  public Long getIdSku() {
    return idSku;
  }
}
