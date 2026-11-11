package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.enums.OrigemMovimentacao;
import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MovimentacaoEstoqueTest {

  @Test
  void entradaNf() {
    Instant dh = Instant.now();
    MovimentacaoEstoque m =
        MovimentacaoEstoque.entradaPorNf(1L, 5)
            .nota("NF-1", "1", "00.000.000/0001-00", LocalDate.now())
            .dataHora(dh)
            .id(10L)
            .construir();
    assertThat(m.getOrigem()).isEqualTo(OrigemMovimentacao.ENTRADA_NF);
    assertThat(m.getQuantidade()).isEqualTo(5);
    assertThat(m.getNumeroNota()).isEqualTo("NF-1");
    assertThat(m.getDataHora()).isEqualTo(dh);
    assertThat(m.getId()).isEqualTo(10L);
    m.definirId(99L);
    assertThat(m.getId()).isEqualTo(99L);
  }

  @Test
  void estornoNfSaleMovimentacaoNegativa() {
    MovimentacaoEstoque m = MovimentacaoEstoque.estornoPorNf(1L, 5).construir();
    assertThat(m.getQuantidade()).isEqualTo(-5);
    assertThat(m.getOrigem()).isEqualTo(OrigemMovimentacao.ESTORNO_NF);
  }

  @Test
  void consumoEOrcamento() {
    MovimentacaoEstoque m =
        MovimentacaoEstoque.consumoPorOrcamento(1L, 2)
            .orcamento("OS-042026-000001", 1, 1)
            .construir();
    assertThat(m.getQuantidade()).isEqualTo(-2);
    assertThat(m.getIdOrdemServico()).isEqualTo("OS-042026-000001");
    assertThat(m.getIdOrcamento()).isEqualTo(1);
    assertThat(m.getIdOrcamentoItem()).isEqualTo(1);
    assertThat(m.getOrigem()).isEqualTo(OrigemMovimentacao.CONSUMO_ORCAMENTO);
    assertThat(m.getSerieNota()).isNull();
    assertThat(m.getCnpjFornecedor()).isNull();
    assertThat(m.getDataEmissao()).isNull();
  }

  @Test
  void devolucao() {
    MovimentacaoEstoque m = MovimentacaoEstoque.devolucaoPorOrcamento(1L, 3).construir();
    assertThat(m.getQuantidade()).isEqualTo(3);
    assertThat(m.getOrigem()).isEqualTo(OrigemMovimentacao.DEVOLUCAO_ORCAMENTO);
  }

  @Test
  void rejeitaInvalidos() {
    MovimentacaoEstoque.Builder semSku = MovimentacaoEstoque.entradaPorNf(null, 1);
    assertThatThrownBy(semSku::construir).isInstanceOf(BusinessException.class);
    MovimentacaoEstoque.Builder qtdZero = MovimentacaoEstoque.entradaPorNf(1L, 0);
    assertThatThrownBy(qtdZero::construir).isInstanceOf(BusinessException.class);
    MovimentacaoEstoque.Builder semOrigem =
        new MovimentacaoEstoque.Builder().idSku(1L).quantidade(1);
    assertThatThrownBy(semOrigem::construir).isInstanceOf(BusinessException.class);
  }
}
