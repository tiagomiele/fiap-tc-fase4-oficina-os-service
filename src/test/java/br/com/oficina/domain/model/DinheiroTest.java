package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DinheiroTest {

  @Test
  void criaEEscala() {
    assertThat(Dinheiro.de("10").valor()).isEqualByComparingTo("10.00");
    assertThat(Dinheiro.de(new BigDecimal("10.005")).valor()).isEqualByComparingTo("10.00");
  }

  @Test
  void somarEMultiplicar() {
    Dinheiro a = Dinheiro.de("10.00");
    assertThat(a.somar(Dinheiro.de("5.50")).valor()).isEqualByComparingTo("15.50");
    assertThat(a.multiplicar(3).valor()).isEqualByComparingTo("30.00");
    assertThat(a.multiplicar(0).valor()).isEqualByComparingTo("0.00");
  }

  @Test
  void rejeitaNegativo() {
    BigDecimal negativo = new BigDecimal("-1");
    assertThatThrownBy(() -> Dinheiro.de(negativo)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> Dinheiro.de((BigDecimal) null)).isInstanceOf(BusinessException.class);
    Dinheiro dez = Dinheiro.de("10");
    assertThatThrownBy(() -> dez.multiplicar(-1)).isInstanceOf(BusinessException.class);
  }

  @Test
  void equalsEHashCode() {
    Dinheiro a = Dinheiro.de("10.00");
    Dinheiro b = Dinheiro.de("10");
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo("10.00");
    assertThat(Dinheiro.ZERO.valor()).isEqualByComparingTo("0.00");
    assertThat(a).hasToString("10.00");
  }
}
