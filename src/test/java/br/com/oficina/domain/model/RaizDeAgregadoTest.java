package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RaizDeAgregadoTest {

  static class Dummy extends RaizDeAgregado {
    void dispara(EventoDominio e) {
      registrarEvento(e);
    }
  }

  record EventoTeste(Instant ocorridoEm, String tipo) implements EventoDominio {}

  @Test
  void registraELimpaEventos() {
    Dummy d = new Dummy();
    assertThat(d.eventosPendentes()).isEmpty();

    d.dispara(null);
    assertThat(d.eventosPendentes()).isEmpty();

    EventoTeste e = new EventoTeste(Instant.now(), "TIPO");
    d.dispara(e);
    assertThat(d.eventosPendentes()).containsExactly(e);
    assertThat(e.tipo()).isEqualTo("TIPO");

    d.limparEventos();
    assertThat(d.eventosPendentes()).isEmpty();
  }
}
