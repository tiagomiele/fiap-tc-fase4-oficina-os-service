package br.com.oficina.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Classe base para raízes de agregado DDD. Mantém eventos pendentes. */
public abstract class RaizDeAgregado {

  private final List<EventoDominio> eventos = new ArrayList<>();

  protected void registrarEvento(EventoDominio evento) {
    if (evento != null) {
      eventos.add(evento);
    }
  }

  public List<EventoDominio> eventosPendentes() {
    return Collections.unmodifiableList(eventos);
  }

  public void limparEventos() {
    eventos.clear();
  }
}
