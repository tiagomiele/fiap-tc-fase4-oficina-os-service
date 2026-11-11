package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PecaTest {

  @Test
  void cria() {
    Peca p = Peca.criar("Filtro", Dinheiro.de("30"));
    assertThat(p.isAtivo()).isTrue();
    assertThat(p.getNome()).isEqualTo("Filtro");
    assertThat(p.getPrecoVenda().valor()).isEqualByComparingTo("30.00");
    p.definirId(9L);
    assertThat(p.getIdSku()).isEqualTo(9L);
  }

  @Test
  void atualizaEDesativa() {
    Peca p = Peca.criar("A", Dinheiro.de("10"));
    p.atualizar("B", Dinheiro.de("20"));
    assertThat(p.getNome()).isEqualTo("B");
    p.desativar();
    assertThat(p.isAtivo()).isFalse();
    assertThatThrownBy(p::desativar).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaInvalidos() {
    Dinheiro umReal = Dinheiro.de("1");
    assertThatThrownBy(() -> Peca.criar(" ", umReal)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> Peca.criar("A", null)).isInstanceOf(BusinessException.class);
  }

  @Test
  void reconstituiComTimestamps() {
    Instant t1 = Instant.parse("2025-01-01T10:00:00Z");
    Instant t2 = Instant.parse("2025-02-01T10:00:00Z");
    Peca p = new Peca(1L, "Filtro", Dinheiro.de("30"), true, 3L, t1, t2);
    assertThat(p.getCriadoEm()).isEqualTo(t1);
    assertThat(p.getAtualizadoEm()).isEqualTo(t2);
    assertThat(p.getVersao()).isEqualTo(3L);
    Peca p2 = new Peca(1L, "F", Dinheiro.de("1"), true, 0L, null, null);
    assertThat(p2.getCriadoEm()).isEqualTo(p2.getAtualizadoEm());
  }
}
