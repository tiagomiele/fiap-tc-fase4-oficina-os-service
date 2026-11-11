package br.com.oficina.usecase;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.Documento;
import br.com.oficina.domain.model.ItemNotaFiscalFornecedor;
import br.com.oficina.domain.model.NotaFiscalFornecedor;
import br.com.oficina.domain.model.NotaFiscalFornecedorId;
import br.com.oficina.usecase.gateway.NotaFiscalFornecedorRepository;
import br.com.oficina.usecase.gateway.PecaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotaFiscalFornecedorServiceImpl {

  public record ItemEntrada(Long idSku, int quantidade, BigDecimal precoUnitario) {}

  private final NotaFiscalFornecedorRepository repo;
  private final PecaRepository pecas;
  private final EstoqueServiceImpl estoque;
  private final FinanceiroServiceImpl financeiro;

  public NotaFiscalFornecedorServiceImpl(
      NotaFiscalFornecedorRepository repo,
      PecaRepository pecas,
      EstoqueServiceImpl estoque,
      FinanceiroServiceImpl financeiro) {
    this.repo = repo;
    this.pecas = pecas;
    this.estoque = estoque;
    this.financeiro = financeiro;
  }

  @Transactional
  public NotaFiscalFornecedor registrar(
      String numeroNota,
      String serieNota,
      String cnpjFornecedor,
      LocalDate dataEmissao,
      String nomeFornecedor,
      List<ItemEntrada> itens) {
    Documento cnpj = Documento.de(cnpjFornecedor);
    if (cnpj.tipo() != Documento.Tipo.CNPJ) {
      throw new BusinessException("NF_INVALIDA", "CNPJ do fornecedor inválido");
    }
    NotaFiscalFornecedorId id =
        new NotaFiscalFornecedorId(numeroNota, serieNota, cnpj.valor(), dataEmissao);
    if (repo.existe(id)) {
      throw new BusinessException("NF_DUPLICADA", "Nota fiscal já registrada");
    }
    List<ItemNotaFiscalFornecedor> itensDominio = new ArrayList<>();
    for (ItemEntrada e : itens) {
      if (pecas.porSku(e.idSku()).isEmpty()) {
        throw new BusinessException("PECA_NAO_CADASTRADA", "Peça não cadastrada: " + e.idSku());
      }
      itensDominio.add(
          new ItemNotaFiscalFornecedor(e.idSku(), e.quantidade(), Dinheiro.de(e.precoUnitario())));
    }
    NotaFiscalFornecedor nf = NotaFiscalFornecedor.emitir(id, nomeFornecedor, itensDominio);
    NotaFiscalFornecedor salva = repo.salvar(nf);
    for (ItemNotaFiscalFornecedor it : salva.getItens()) {
      estoque.entradaPorNf(
          it.getIdSku(),
          it.getQuantidade(),
          id.numeroNota(),
          id.serieNota(),
          id.cnpjFornecedor(),
          id.dataEmissao());
    }
    financeiro.lancarContaAPagarNF(
        salva.getValorTotal(),
        id.numeroNota(),
        id.serieNota(),
        id.cnpjFornecedor(),
        id.dataEmissao(),
        "NF " + id.numeroNota() + "/" + id.serieNota() + " de " + salva.getNomeFornecedor());
    return salva;
  }

  @Transactional
  public void estornar(
      String numeroNota, String serieNota, String cnpjFornecedor, LocalDate dataEmissao) {
    NotaFiscalFornecedorId id =
        new NotaFiscalFornecedorId(
            numeroNota, serieNota, Documento.de(cnpjFornecedor).valor(), dataEmissao);
    NotaFiscalFornecedor nf =
        repo.porId(id)
            .orElseThrow(
                () -> new BusinessException("NF_NAO_ENCONTRADA", "Nota fiscal não encontrada"));
    nf.estornar();
    repo.salvar(nf);
    for (ItemNotaFiscalFornecedor it : nf.getItens()) {
      estoque.estornoPorNf(
          it.getIdSku(),
          it.getQuantidade(),
          id.numeroNota(),
          id.serieNota(),
          id.cnpjFornecedor(),
          id.dataEmissao());
    }
    financeiro.estornarPorNf(
        id.numeroNota(), id.serieNota(), id.cnpjFornecedor(), id.dataEmissao());
  }

  @Transactional(readOnly = true)
  public List<NotaFiscalFornecedor> listar() {
    return repo.listarTodas();
  }

  @Transactional(readOnly = true)
  public NotaFiscalFornecedor buscar(
      String numero, String serie, String cnpj, LocalDate dataEmissao) {
    return repo.porId(
            new NotaFiscalFornecedorId(numero, serie, Documento.de(cnpj).valor(), dataEmissao))
        .orElseThrow(
            () -> new BusinessException("NF_NAO_ENCONTRADA", "Nota fiscal não encontrada"));
  }
}
