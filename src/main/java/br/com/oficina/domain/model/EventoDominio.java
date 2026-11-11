package br.com.oficina.domain.model;

import java.time.Instant;

/** Marker de evento de domínio. */
public interface EventoDominio {
  Instant ocorridoEm();

  String tipo();
}
