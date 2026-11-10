package br.com.oficina.usecase;

import br.com.oficina.domain.enums.TipoLancamento;
import br.com.oficina.domain.model.Dinheiro;
import br.com.oficina.domain.model.LancamentoFinanceiro;
import br.com.oficina.usecase.gateway.LancamentoFinanceiroRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceiroServiceImpl {

  private final LancamentoFinanceiroRepository repo;

  public FinanceiroServiceImpl(LancamentoFinanceiroRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public LancamentoFinanceiro lancarContaAPagarNF(
      Dinheiro valor,
      String numeroNota,
      String serieNota,
      String cnpjFornecedor,
      LocalDate dataEmissao,
      String descricao) {
    return repo.salvar(
        LancamentoFinanceiro.contaAPagarNF(
            valor, numeroNota, serieNota, cnpjFornecedor, dataEmissao, descricao));
  }

  @Transactional
  public LancamentoFinanceiro lancarContaAReceberOS(
      Dinheiro valor, String idOrdemServico, String descricao) {
    return repo.salvar(LancamentoFinanceiro.contaAReceberOS(valor, idOrdemServico, descricao));
  }

  @Transactional
  public void estornarPorNf(
      String numeroNota, String serieNota, String cnpj, LocalDate dataEmissao) {
    Optional<LancamentoFinanceiro> opt = repo.porNf(numeroNota, serieNota, cnpj, dataEmissao);
    opt.ifPresent(
        l -> {
          l.estornar();
          repo.salvar(l);
        });
  }

  @Transactional(readOnly = true)
  public List<LancamentoFinanceiro> contasAPagar() {
    return repo.porTipo(TipoLancamento.CONTAS_A_PAGAR);
  }

  @Transactional(readOnly = true)
  public List<LancamentoFinanceiro> contasAReceber() {
    return repo.porTipo(TipoLancamento.CONTAS_A_RECEBER);
  }
}
