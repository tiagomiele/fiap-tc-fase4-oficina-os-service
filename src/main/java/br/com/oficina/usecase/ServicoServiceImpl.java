package br.com.oficina.usecase;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.Servico;
import br.com.oficina.usecase.gateway.ServicoRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoServiceImpl {

  private static final String ERRO_SERVICO_NAO_CADASTRADO = "SERVICO_NAO_CADASTRADO";
  private static final String MSG_SERVICO_NAO_CADASTRADO = "Serviço não cadastrado";

  private final ServicoRepository repo;

  public ServicoServiceImpl(ServicoRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public Servico cadastrar(String nome, String descricao, BigDecimal precoBase) {
    return repo.salvar(Servico.criar(nome, descricao, Dinheiro.de(precoBase)));
  }

  @Transactional
  public Servico atualizar(Long id, String nome, String descricao, BigDecimal precoBase) {
    Servico s =
        repo.porId(id)
            .orElseThrow(
                () ->
                    new BusinessException(ERRO_SERVICO_NAO_CADASTRADO, MSG_SERVICO_NAO_CADASTRADO));
    s.atualizar(nome, descricao, Dinheiro.de(precoBase));
    return repo.salvar(s);
  }

  @Transactional
  public void desativar(Long id) {
    Servico s =
        repo.porId(id)
            .orElseThrow(
                () ->
                    new BusinessException(ERRO_SERVICO_NAO_CADASTRADO, MSG_SERVICO_NAO_CADASTRADO));
    if (repo.temOsAtiva(id)) {
      throw new BusinessException("SERVICO_COM_OS_ATIVA", "Serviço possui OS ativa");
    }
    s.desativar();
    repo.salvar(s);
  }

  @Transactional(readOnly = true)
  public Servico buscar(Long id) {
    return repo.porId(id)
        .orElseThrow(
            () -> new BusinessException(ERRO_SERVICO_NAO_CADASTRADO, MSG_SERVICO_NAO_CADASTRADO));
  }

  @Transactional(readOnly = true)
  public List<Servico> listar() {
    return repo.listarTodos();
  }
}
