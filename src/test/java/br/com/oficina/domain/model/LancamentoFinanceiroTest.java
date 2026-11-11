package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.enums.OrigemLancamento;
import br.com.oficina.domain.enums.TipoLancamento;
import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LancamentoFinanceiroTest {

  @Test
  void contaAPagarNfECamposPreenchidos() {
    LancamentoFinanceiro l =
        LancamentoFinanceiro.contaAPagarNF(
            Dinheiro.de("100"), "NF1", "1", "00.000.000/0001-00", LocalDate.now(), "desc");
    assertThat(l.getTipo()).isEqualTo(TipoLancamento.CONTAS_A_PAGAR);
    assertThat(l.getOrigem()).isEqualTo(OrigemLancamento.NF_FORNECEDOR);
    assertThat(l.getNumeroNota()).isEqualTo("NF1");
    assertThat(l.getSerieNota()).isEqualTo("1");
    assertThat(l.getCnpjFornecedor()).isEqualTo("00.000.000/0001-00");
    assertThat(l.getDescricao()).isEqualTo("desc");
    assertThat(l.isEstornado()).isFalse();
    l.definirId(1L);
    assertThat(l.getId()).isEqualTo(1L);
    assertThat(l.getCriadoEm()).isNotNull();
    assertThat(l.getAtualizadoEm()).isNotNull();
    assertThat(l.getIdOrdemServico()).isNull();
  }

  @Test
  void contaAReceberOs() {
    LancamentoFinanceiro l =
        LancamentoFinanceiro.contaAReceberOS(Dinheiro.de("500"), "OS-042026-000001", "OS paga");
    assertThat(l.getTipo()).isEqualTo(TipoLancamento.CONTAS_A_RECEBER);
    assertThat(l.getOrigem()).isEqualTo(OrigemLancamento.OS_PAGAMENTO);
    assertThat(l.getIdOrdemServico()).isEqualTo("OS-042026-000001");
    assertThat(l.getDataLancamento()).isNotNull();
    assertThat(l.getDataEmissao()).isNull();
  }

  @Test
  void estornarNaoPodeRepetir() {
    LancamentoFinanceiro l =
        LancamentoFinanceiro.contaAReceberOS(Dinheiro.de("10"), "OS-042026-000001", null);
    l.estornar();
    assertThat(l.isEstornado()).isTrue();
    assertThatThrownBy(l::estornar).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaValorNulo() {
    assertThatThrownBy(() -> LancamentoFinanceiro.contaAReceberOS(null, "OS-1", null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void reconstituir() {
    Instant t = Instant.now();
    LancamentoFinanceiro l =
        LancamentoFinanceiro.reconstituir(
            7L,
            TipoLancamento.CONTAS_A_PAGAR,
            OrigemLancamento.NF_FORNECEDOR,
            Dinheiro.de("1"),
            LocalDate.now(),
            "d",
            "n",
            "s",
            "c",
            LocalDate.now(),
            null,
            true,
            t,
            t);
    assertThat(l.getId()).isEqualTo(7L);
    assertThat(l.isEstornado()).isTrue();
  }

  @Test
  void reconstituirComNullsAplicaDefaults() {
    LancamentoFinanceiro l =
        LancamentoFinanceiro.reconstituir(
            1L,
            TipoLancamento.CONTAS_A_RECEBER,
            OrigemLancamento.OS_PAGAMENTO,
            Dinheiro.de("10"),
            null,
            null,
            null,
            null,
            null,
            null,
            "OS-1",
            false,
            null,
            null);
    assertThat(l.getDataLancamento()).isNotNull();
    assertThat(l.getCriadoEm()).isNotNull();
    assertThat(l.getAtualizadoEm()).isEqualTo(l.getCriadoEm());
  }
}
