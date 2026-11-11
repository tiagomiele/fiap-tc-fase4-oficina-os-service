package br.com.oficina.adapter.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.oficina.domain.enums.StatusOrdemServico;
import br.com.oficina.domain.model.NumeroOS;
import br.com.oficina.domain.model.OrdemServico;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrdemServicoStatusResponseTest {

  @ParameterizedTest
  @EnumSource(StatusOrdemServico.class)
  void descreveTodosOsStatus(StatusOrdemServico status) {
    OrdemServico os = mock(OrdemServico.class);
    NumeroOS numero = mock(NumeroOS.class);
    when(numero.valor()).thenReturn("OS-102026-000001");
    when(os.getNumero()).thenReturn(numero);
    when(os.getStatus()).thenReturn(status);

    OrdemServicoStatusResponse r = OrdemServicoStatusResponse.de(os);

    assertThat(r.numeroOs()).isEqualTo("OS-102026-000001");
    assertThat(r.status()).isEqualTo(status.name());
    assertThat(r.statusDescricao()).isNotBlank();
  }
}
