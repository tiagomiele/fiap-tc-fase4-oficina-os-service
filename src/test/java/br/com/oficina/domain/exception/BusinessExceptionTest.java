package br.com.oficina.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

  @Test
  void guardaCodigoEMensagem() {
    BusinessException e = new BusinessException("CODIGO", "mensagem");
    assertThat(e.getCodigo()).isEqualTo("CODIGO");
    assertThat(e.getMessage()).isEqualTo("mensagem");
  }
}
