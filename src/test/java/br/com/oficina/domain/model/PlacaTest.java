package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

class PlacaTest {

  @Test
  void aceitaPlacaAntigaNormalizada() {
    assertThat(Placa.de("abc-1234").valor()).isEqualTo("ABC1234");
  }

  @Test
  void aceitaPlacaMercosul() {
    assertThat(Placa.de("abc1d23").valor()).isEqualTo("ABC1D23");
  }

  @Test
  void rejeitaInvalida() {
    assertThatThrownBy(() -> Placa.de("XX-999")).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaNula() {
    assertThatThrownBy(() -> Placa.de(null)).isInstanceOf(BusinessException.class);
  }

  @Test
  void equalsEHashCode() {
    Placa a = Placa.de("ABC1234");
    Placa b = Placa.de("abc-1234");
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo("ABC1234");
    assertThat(a).hasToString("ABC1234");
  }
}
