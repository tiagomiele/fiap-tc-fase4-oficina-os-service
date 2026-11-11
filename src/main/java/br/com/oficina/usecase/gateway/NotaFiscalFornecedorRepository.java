package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.model.NotaFiscalFornecedor;
import br.com.oficina.domain.model.NotaFiscalFornecedorId;
import java.util.List;
import java.util.Optional;

public interface NotaFiscalFornecedorRepository {
  NotaFiscalFornecedor salvar(NotaFiscalFornecedor nf);

  Optional<NotaFiscalFornecedor> porId(NotaFiscalFornecedorId id);

  boolean existe(NotaFiscalFornecedorId id);

  List<NotaFiscalFornecedor> listarTodas();
}
