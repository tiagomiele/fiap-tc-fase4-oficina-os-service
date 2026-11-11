package br.com.oficina.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mensagens_processadas")
public class MensagemProcessadaJpaEntity {

  @Id
  @Column(name = "message_id", length = 100)
  private String messageId;

  @Column(nullable = false, length = 60)
  private String tipo;

  @Column(name = "numero_os", nullable = false, length = 30)
  private String numeroOs;

  @Column(name = "correlation_id", length = 100)
  private String correlationId;

  @Column(name = "processada_em", nullable = false)
  private Instant processadaEm;

  protected MensagemProcessadaJpaEntity() {}

  public MensagemProcessadaJpaEntity(
      String messageId, String tipo, String numeroOs, String correlationId, Instant processadaEm) {
    this.messageId = messageId;
    this.tipo = tipo;
    this.numeroOs = numeroOs;
    this.correlationId = correlationId;
    this.processadaEm = processadaEm;
  }

  public String getMessageId() {
    return messageId;
  }
}
