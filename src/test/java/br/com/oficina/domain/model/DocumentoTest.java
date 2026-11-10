package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

class DocumentoTest {

  @Test
  void aceitaCpfValido() {
    Documento d = Documento.de("529.982.247-25");
    assertThat(d.tipo()).isEqualTo(Documento.Tipo.CPF);
    assertThat(d.valor()).isEqualTo("52998224725");
  }

  @Test
  void aceitaCnpjValido() {
    Documento d = Documento.de("04.252.011/0001-10");
    assertThat(d.tipo()).isEqualTo(Documento.Tipo.CNPJ);
    assertThat(d.valor()).isEqualTo("04252011000110");
  }

  @Test
  void rejeitaCpfInvalido() {
    assertThatThrownBy(() -> Documento.de("111.111.111-11"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("CPF inválido");
  }

  @Test
  void rejeitaCnpjInvalido() {
    assertThatThrownBy(() -> Documento.de("11.111.111/1111-11"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("CNPJ inválido");
  }

  @Test
  void rejeitaTamanhoInvalido() {
    assertThatThrownBy(() -> Documento.de("1234")).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaNulo() {
    assertThatThrownBy(() -> Documento.de(null)).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaCpfDigitoFinalErrado() {
    assertThatThrownBy(() -> Documento.de("52998224726")).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaCnpjDigitoFinalErrado() {
    assertThatThrownBy(() -> Documento.de("04252011000111")).isInstanceOf(BusinessException.class);
  }

  @Test
  void equalsEHashCode() {
    Documento a = Documento.de("529.982.247-25");
    Documento b = Documento.de("52998224725");
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo("string");
    assertThat(a).hasToString("52998224725");
  }
}
