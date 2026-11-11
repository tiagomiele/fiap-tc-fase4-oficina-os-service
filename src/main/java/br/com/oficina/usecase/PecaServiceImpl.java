package br.com.oficina.usecase;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.Peca;
import br.com.oficina.usecase.gateway.PecaRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PecaServiceImpl {

  private static final String ERRO_PECA_NAO_CADASTRADA = "PECA_NAO_CADASTRADA";
  private static final String MSG_PECA_NAO_CADASTRADA = "Peça não cadastrada";

  private final PecaRepository repo;

  public PecaServiceImpl(PecaRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public Peca cadastrar(String nome, BigDecimal precoVenda) {
    return repo.salvar(Peca.criar(nome, Dinheiro.de(precoVenda)));
  }

  @Transactional
  public Peca atualizar(Long id, String nome, BigDecimal precoVenda) {
    Peca p =
        repo.porSku(id)
            .orElseThrow(
                () -> new BusinessException(ERRO_PECA_NAO_CADASTRADA, MSG_PECA_NAO_CADASTRADA));
    p.atualizar(nome, Dinheiro.de(precoVenda));
    return repo.salvar(p);
  }

  @Transactional
  public void desativar(Long id) {
    Peca p =
        repo.porSku(id)
            .orElseThrow(
                () -> new BusinessException(ERRO_PECA_NAO_CADASTRADA, MSG_PECA_NAO_CADASTRADA));
    if (repo.temOsAtiva(id)) {
      throw new BusinessException("PECA_COM_OS_ATIVA", "Peça possui OS ativa");
    }
    p.desativar();
    repo.salvar(p);
  }

  @Transactional(readOnly = true)
  public Peca buscar(Long id) {
    return repo.porSku(id)
        .orElseThrow(
            () -> new BusinessException(ERRO_PECA_NAO_CADASTRADA, MSG_PECA_NAO_CADASTRADA));
  }

  @Transactional(readOnly = true)
  public List<Peca> listar() {
    return repo.listarTodas();
  }
}
