package br.com.oficina.adapter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.oficina.adapter.controller.InternoClienteController.AutenticacaoClienteRequest;
import br.com.oficina.usecase.ClienteServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InternoClienteControllerTest {

  private static final String CHAVE = "chave-tecnica-com-mais-de-32-caracteres";
  private static final String CPF = "52998224725";

  private final ClienteServiceImpl clientes = mock(ClienteServiceImpl.class);
  private final InternoClienteController controller = new InternoClienteController(clientes, CHAVE);

  @Test
  void clienteAtivoRetornaId() {
    when(clientes.idClienteAtivoPorCpf(CPF)).thenReturn(Optional.of(3L));

    var resposta = controller.autenticar(CHAVE, new AutenticacaoClienteRequest(CPF));

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody().idCliente()).isEqualTo(3L);
  }

  @Test
  void cpfNaoElegivelRetorna404() {
    when(clientes.idClienteAtivoPorCpf(CPF)).thenReturn(Optional.empty());

    assertThat(controller.autenticar(CHAVE, new AutenticacaoClienteRequest(CPF)).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(controller.autenticar(CHAVE, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void chaveAusenteOuErradaRetorna401() {
    var request = new AutenticacaoClienteRequest(CPF);

    assertThat(controller.autenticar(null, request).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(controller.autenticar("outra", request).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(clientes);
  }

  @Test
  void semChaveConfiguradaRecusaTudo() {
    var semChave = new InternoClienteController(clientes, "");

    assertThat(semChave.autenticar("", new AutenticacaoClienteRequest(CPF)).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    verifyNoInteractions(clientes);
  }
}
