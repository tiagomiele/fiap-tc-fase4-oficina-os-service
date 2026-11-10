package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.enums.TipoLancamento;
import br.com.oficina.domain.model.LancamentoFinanceiro;
import java.util.List;
import java.util.Optional;

public interface LancamentoFinanceiroRepository {
  LancamentoFinanceiro salvar(LancamentoFinanceiro l);

  Optional<LancamentoFinanceiro> porId(Long id);

  List<LancamentoFinanceiro> porTipo(TipoLancamento tipo);

  Optional<LancamentoFinanceiro> porNf(
      String numeroNota, String serieNota, String cnpj, java.time.LocalDate dataEmissao);
}
