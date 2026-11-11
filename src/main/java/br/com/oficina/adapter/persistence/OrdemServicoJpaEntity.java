package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.enums.StatusOrdemServico;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ordens_servico")
public class OrdemServicoJpaEntity {

  @Id
  @Column(name = "id_ordem_servico", length = 20)
  private String idOrdemServico;

  @Column(name = "id_cliente", nullable = false)
  private Long idCliente;

  @Column(name = "id_placa", length = 8, nullable = false)
  private String idPlaca;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private StatusOrdemServico status;

  @Column(name = "descricao_problema", length = 1000)
  private String descricaoProblema;

  @Column(name = "valor_total_conserto", nullable = false, precision = 14, scale = 2)
  private BigDecimal valorTotalConserto;

  @Column(name = "motivo_rejeicao", length = 500)
  private String motivoRejeicao;

  @Column(name = "comprovante_pagamento", length = 255)
  private String comprovantePagamento;

  @Column(name = "orcamento_atual", nullable = false)
  private Integer orcamentoAtual;

  @Version
  @Column(nullable = false)
  private Long versao;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private Instant atualizadoEm;

  @Column(name = "inicio_execucao")
  private Instant inicioExecucao;

  @Column(name = "fim_execucao")
  private Instant fimExecucao;

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
  @JoinColumn(name = "id_ordem_servico")
  private List<ItemOrcamentoJpaEntity> itens = new ArrayList<>();

  protected OrdemServicoJpaEntity() {}

  public OrdemServicoJpaEntity(String id) {
    this.idOrdemServico = id;
  }

  public String getIdOrdemServico() {
    return idOrdemServico;
  }

  public void setIdOrdemServico(String v) {
    this.idOrdemServico = v;
  }

  public Long getIdCliente() {
    return idCliente;
  }

  public void setIdCliente(Long v) {
    this.idCliente = v;
  }

  public String getIdPlaca() {
    return idPlaca;
  }

  public void setIdPlaca(String v) {
    this.idPlaca = v;
  }

  public StatusOrdemServico getStatus() {
    return status;
  }

  public void setStatus(StatusOrdemServico v) {
    this.status = v;
  }

  public String getDescricaoProblema() {
    return descricaoProblema;
  }

  public void setDescricaoProblema(String v) {
    this.descricaoProblema = v;
  }

  public BigDecimal getValorTotalConserto() {
    return valorTotalConserto;
  }

  public void setValorTotalConserto(BigDecimal v) {
    this.valorTotalConserto = v;
  }

  public String getMotivoRejeicao() {
    return motivoRejeicao;
  }

  public void setMotivoRejeicao(String v) {
    this.motivoRejeicao = v;
  }

  public String getComprovantePagamento() {
    return comprovantePagamento;
  }

  public void setComprovantePagamento(String v) {
    this.comprovantePagamento = v;
  }

  public Integer getOrcamentoAtual() {
    return orcamentoAtual;
  }

  public void setOrcamentoAtual(Integer v) {
    this.orcamentoAtual = v;
  }

  public Long getVersao() {
    return versao;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public void setCriadoEm(Instant v) {
    this.criadoEm = v;
  }

  public Instant getAtualizadoEm() {
    return atualizadoEm;
  }

  public void setAtualizadoEm(Instant v) {
    this.atualizadoEm = v;
  }

  public List<ItemOrcamentoJpaEntity> getItens() {
    return itens;
  }

  public Instant getInicioExecucao() {
    return inicioExecucao;
  }

  public void setInicioExecucao(Instant v) {
    this.inicioExecucao = v;
  }

  public Instant getFimExecucao() {
    return fimExecucao;
  }

  public void setFimExecucao(Instant v) {
    this.fimExecucao = v;
  }
}
