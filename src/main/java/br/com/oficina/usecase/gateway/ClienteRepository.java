package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.model.Cliente;
import br.com.oficina.domain.model.Documento;
import java.util.List;
import java.util.Optional;

public interface ClienteRepository {
  Cliente salvar(Cliente cliente);

  Optional<Cliente> porId(Long id);

  Optional<Cliente> porDocumento(Documento documento);

  boolean existePorDocumento(Documento documento);

  List<Cliente> listarTodos();

  boolean temOsAtiva(Long idCliente);
}
