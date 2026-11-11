package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.Servico;
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
@Table(name = "servicos")
public class ServicoJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_servico")
  private Long idServico;

  @Column(nullable = false)
  private String nome;

  @Column(length = 500)
  private String descricao;

  @Column(name = "preco_base", nullable = false, precision = 12, scale = 2)
  private BigDecimal precoBase;

  @Column(nullable = false)
  private boolean ativo;

  @Version
  @Column(nullable = false)
  private Long versao;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private Instant atualizadoEm;

  protected ServicoJpaEntity() {}

  public ServicoJpaEntity(Servico s) {
    atualizarDe(s);
    this.criadoEm = s.getCriadoEm();
  }

  public void atualizarDe(Servico s) {
    this.idServico = s.getIdServico();
    this.nome = s.getNome();
    this.descricao = s.getDescricao();
    this.precoBase = s.getPrecoBase().valor();
    this.ativo = s.isAtivo();
    this.atualizadoEm = s.getAtualizadoEm();
  }

  public Servico toDomain() {
    return new Servico(
        idServico,
        nome,
        descricao,
        Dinheiro.de(precoBase),
        ativo,
        versao == null ? 0L : versao,
        criadoEm,
        atualizadoEm);
  }

  public Long getIdServico() {
    return idServico;
  }
}
