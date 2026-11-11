package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

class NumeroOSTest {

  @Test
  void geraEParseia() {
    NumeroOS n = NumeroOS.gerar(4, 2026, 1);
    assertThat(n.valor()).isEqualTo("OS-042026-000001");
    assertThat(n.mes()).isEqualTo(4);
    assertThat(n.ano()).isEqualTo(2026);
    assertThat(n.sequencial()).isEqualTo(1);
    assertThat(n).hasToString("OS-042026-000001");
  }

  @Test
  void equalsEHashCodeDependemDoValor() {
    NumeroOS a = NumeroOS.de("OS-042026-000001");
    NumeroOS b = NumeroOS.de("OS-042026-000001");
    NumeroOS c = NumeroOS.de("OS-042026-000002");
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c).isNotEqualTo("outro");
  }

  @Test
  void rejeitaFormatoInvalido() {
    assertThatThrownBy(() -> NumeroOS.de(null)).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> NumeroOS.de("invalid")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> NumeroOS.de("OS-132026-000001")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> NumeroOS.de("OS-041999-000001")).isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> NumeroOS.de("OS-042026-000000")).isInstanceOf(BusinessException.class);
  }
}
