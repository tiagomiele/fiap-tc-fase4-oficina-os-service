package br.com.oficina.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.oficina.domain.model.Cliente;
import br.com.oficina.domain.model.Documento;
import br.com.oficina.usecase.gateway.ClienteRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClienteServiceImplAutenticacaoTest {

  private static final String CPF = "529.982.247-25";

  private final ClienteRepository repo = mock(ClienteRepository.class);
  private final ClienteServiceImpl service = new ClienteServiceImpl(repo);

  @Test
  void devolveIdDoClienteAtivo() {
    Cliente cliente = Cliente.criar("Ana", Documento.de(CPF), "ana@example.com", "11999999999");
    cliente.definirId(7L);
    when(repo.porDocumento(Documento.de(CPF))).thenReturn(Optional.of(cliente));

    assertThat(service.idClienteAtivoPorCpf(CPF)).contains(7L);
  }

  @Test
  void clienteInativoNaoAutentica() {
    Cliente cliente = Cliente.criar("Ana", Documento.de(CPF), "ana@example.com", "11999999999");
    cliente.definirId(7L);
    cliente.desativar();
    when(repo.porDocumento(Documento.de(CPF))).thenReturn(Optional.of(cliente));

    assertThat(service.idClienteAtivoPorCpf(CPF)).isEmpty();
  }

  @Test
  void clienteInexistenteNaoAutentica() {
    when(repo.porDocumento(any())).thenReturn(Optional.empty());

    assertThat(service.idClienteAtivoPorCpf(CPF)).isEmpty();
  }

  @Test
  void cpfInvalidoOuCnpjNaoConsultaOBanco() {
    assertThat(service.idClienteAtivoPorCpf("111.111.111-11")).isEmpty();
    assertThat(service.idClienteAtivoPorCpf(null)).isEmpty();
    assertThat(service.idClienteAtivoPorCpf("11.222.333/0001-81")).isEmpty();
    verify(repo, never()).porDocumento(any());
  }
}
