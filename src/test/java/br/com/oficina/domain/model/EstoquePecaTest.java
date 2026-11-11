package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

class EstoquePecaTest {

  @Test
  void inicialZero() {
    EstoquePeca e = EstoquePeca.inicial(1L);
    assertThat(e.getIdSku()).isEqualTo(1L);
    assertThat(e.getQuantidade()).isZero();
    assertThat(e.getDataHora()).isNotNull();
  }

  @Test
  void entrarESair() {
    EstoquePeca e = EstoquePeca.inicial(1L);
    e.entrar(10);
    assertThat(e.getQuantidade()).isEqualTo(10);
    e.sair(4);
    assertThat(e.getQuantidade()).isEqualTo(6);
  }

  @Test
  void rejeitaQuantidades() {
    EstoquePeca e = EstoquePeca.inicial(1L);
    assertThatThrownBy(() -> e.entrar(0)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> e.sair(0)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> e.sair(1))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("insuficiente");
  }

  @Test
  void rejeitaConstrutorInvalido() {
    assertThatThrownBy(() -> new EstoquePeca(null, 0, null)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new EstoquePeca(1L, -1, null)).isInstanceOf(BusinessException.class);
  }
}
