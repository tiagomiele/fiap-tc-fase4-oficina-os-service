package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.Placa;
import br.com.oficina.domain.model.Veiculo;
import br.com.oficina.domain.model.VeiculoId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "veiculos")
@IdClass(VeiculoIdJpa.class)
public class VeiculoJpaEntity {

  @Id
  @Column(name = "id_placa", nullable = false, length = 8)
  private String idPlaca;

  @Id
  @Column(name = "id_cliente", nullable = false)
  private Long idCliente;

  @Column(nullable = false, length = 64)
  private String marca;

  @Column(nullable = false, length = 64)
  private String modelo;

  @Column(nullable = false)
  private int ano;

  @Column(nullable = false)
  private boolean ativo;

  @Version
  @Column(nullable = false)
  private Long versao;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private Instant atualizadoEm;

  protected VeiculoJpaEntity() {}

  public VeiculoJpaEntity(Veiculo v) {
    this.idPlaca = v.getId().placa().valor();
    this.idCliente = v.getId().idCliente();
    this.criadoEm = v.getCriadoEm();
    atualizarDe(v);
  }

  public void atualizarDe(Veiculo v) {
    this.marca = v.getMarca();
    this.modelo = v.getModelo();
    this.ano = v.getAno();
    this.ativo = v.isAtivo();
    this.atualizadoEm = v.getAtualizadoEm();
  }

  public Veiculo toDomain() {
    return new Veiculo(
        new VeiculoId(Placa.de(idPlaca), idCliente),
        marca,
        modelo,
        ano,
        ativo,
        versao == null ? 0L : versao,
        criadoEm,
        atualizadoEm);
  }

  public String getIdPlaca() {
    return idPlaca;
  }

  public Long getIdCliente() {
    return idCliente;
  }
}
