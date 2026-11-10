package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.model.HistoricoStatusOrdemServico;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "historico_status_ordem_servico")
public class HistoricoStatusOrdemServicoJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "id_ordem_servico", nullable = false, length = 20)
  private String idOrdemServico;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private StatusOrdemServico status;

  @Column(name = "entrada_em", nullable = false)
  private Instant entradaEm;

  @Column(name = "saida_em")
  private Instant saidaEm;

  @Column(name = "duracao_milisegundos")
  private Long duracaoMilisegundos;

  @Column(name = "correlation_id", nullable = false, length = 64)
  private String correlationId;

  protected HistoricoStatusOrdemServicoJpaEntity() {}

  public HistoricoStatusOrdemServicoJpaEntity(
      String idOrdemServico, StatusOrdemServico status, Instant entradaEm, String correlationId) {
    this.idOrdemServico = idOrdemServico;
    this.status = status;
    this.entradaEm = entradaEm;
    this.correlationId = correlationId;
  }

  public void encerrar(Instant instante) {
    this.saidaEm = instante;
    this.duracaoMilisegundos = Math.max(0, Duration.between(entradaEm, instante).toMillis());
  }

  public HistoricoStatusOrdemServico toDomain() {
    return new HistoricoStatusOrdemServico(
        status, entradaEm, saidaEm, duracaoMilisegundos, correlationId);
  }
}
