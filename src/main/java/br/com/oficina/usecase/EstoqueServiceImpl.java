package br.com.oficina.usecase;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.model.EstoquePeca;
import br.com.oficina.domain.model.MovimentacaoEstoque;
import br.com.oficina.usecase.gateway.EstoqueRepository;
import br.com.oficina.usecase.gateway.PecaRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstoqueServiceImpl {

  private final EstoqueRepository repo;
  private final PecaRepository pecas;

  public EstoqueServiceImpl(EstoqueRepository repo, PecaRepository pecas) {
    this.repo = repo;
    this.pecas = pecas;
  }

  @Transactional
  public void entradaPorNf(
      Long idSku,
      int quantidade,
      String numeroNota,
      String serieNota,
      String cnpj,
      LocalDate dataEmissao) {
    validarPecaExiste(idSku);
    EstoquePeca e = repo.porSku(idSku).orElseGet(() -> EstoquePeca.inicial(idSku));
    e.entrar(quantidade);
    repo.salvar(e);
    repo.registrar(
        MovimentacaoEstoque.entradaPorNf(idSku, quantidade)
            .nota(numeroNota, serieNota, cnpj, dataEmissao)
            .construir());
  }

  @Transactional
  public void estornoPorNf(
      Long idSku,
      int quantidade,
      String numeroNota,
      String serieNota,
      String cnpj,
      LocalDate dataEmissao) {
    EstoquePeca e =
        repo.porSku(idSku)
            .orElseThrow(
                () -> new BusinessException("ESTOQUE_INSUFICIENTE", "Sem saldo para estorno"));
    e.sair(quantidade);
    repo.salvar(e);
    repo.registrar(
        MovimentacaoEstoque.estornoPorNf(idSku, quantidade)
            .nota(numeroNota, serieNota, cnpj, dataEmissao)
            .construir());
  }

  @Transactional
  public void consumoPorOrcamento(
      Long idSku,
      int quantidade,
      String idOrdemServico,
      Integer idOrcamento,
      Integer idOrcamentoItem) {
    validarPecaExiste(idSku);
    EstoquePeca e =
        repo.porSku(idSku)
            .orElseThrow(
                () -> new BusinessException("ESTOQUE_INSUFICIENTE", "Estoque insuficiente"));
    e.sair(quantidade);
    repo.salvar(e);
    repo.registrar(
        MovimentacaoEstoque.consumoPorOrcamento(idSku, quantidade)
            .orcamento(idOrdemServico, idOrcamento, idOrcamentoItem)
            .construir());
  }

  @Transactional
  public void devolucaoPorOrcamento(
      Long idSku,
      int quantidade,
      String idOrdemServico,
      Integer idOrcamento,
      Integer idOrcamentoItem) {
    validarPecaExiste(idSku);
    EstoquePeca e = repo.porSku(idSku).orElseGet(() -> EstoquePeca.inicial(idSku));
    e.entrar(quantidade);
    repo.salvar(e);
    repo.registrar(
        MovimentacaoEstoque.devolucaoPorOrcamento(idSku, quantidade)
            .orcamento(idOrdemServico, idOrcamento, idOrcamentoItem)
            .construir());
  }

  @Transactional(readOnly = true)
  public int saldo(Long idSku) {
    return repo.porSku(idSku).map(EstoquePeca::getQuantidade).orElse(0);
  }

  @Transactional(readOnly = true)
  public List<EstoquePeca> listar() {
    return repo.listarTodos();
  }

  private void validarPecaExiste(Long idSku) {
    if (pecas.porSku(idSku).isEmpty()) {
      throw new BusinessException("PECA_NAO_CADASTRADA", "Peça não cadastrada: " + idSku);
    }
  }
}
