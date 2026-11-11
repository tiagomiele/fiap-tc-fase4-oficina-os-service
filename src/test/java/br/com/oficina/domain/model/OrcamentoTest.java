package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.enums.StatusOrcamentoItem;
import br.com.oficina.domain.enums.TipoItem;
import br.com.oficina.domain.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrcamentoTest {

  private ItemOrcamento item(int id, StatusOrcamentoItem s) {
    return new ItemOrcamento(1, id, TipoItem.SERVICO, s, 10L, "x", 1, Dinheiro.de("10"));
  }

  @Test
  void vazioTemStatusEmAberto() {
    Orcamento o = new Orcamento(1, new ArrayList<>());
    assertThat(o.status()).isEqualTo(StatusOrcamentoItem.EM_ABERTO);
    assertThat(o.getItens()).isEmpty();
    assertThat(o.proximoIdItem()).isEqualTo(1);
    assertThat(o.valorTotalNaoCancelado()).isEqualTo(Dinheiro.ZERO);
  }

  @Test
  void comItensEmAberto() {
    Orcamento o =
        new Orcamento(
            1,
            List.of(
                item(1, StatusOrcamentoItem.EM_ABERTO), item(2, StatusOrcamentoItem.EM_ABERTO)));
    assertThat(o.status()).isEqualTo(StatusOrcamentoItem.EM_ABERTO);
    assertThat(o.valorTotalNaoCancelado().valor()).isEqualByComparingTo("20.00");
    assertThat(o.proximoIdItem()).isEqualTo(3);
    assertThat(o.getIdOrcamento()).isEqualTo(1);
  }

  @Test
  void rejeitaIdInvalido() {
    List<ItemOrcamento> vazia = new ArrayList<>();
    assertThatThrownBy(() -> new Orcamento(0, vazia)).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaStatusHeterogeneos() {
    List<ItemOrcamento> heterogeneos =
        List.of(item(1, StatusOrcamentoItem.EM_ABERTO), item(2, StatusOrcamentoItem.FINALIZADO));
    assertThatThrownBy(() -> new Orcamento(1, heterogeneos))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("compartilhar");
  }

  @Test
  void construtorCopiaListaENaoCompartilhaReferencia() {
    List<ItemOrcamento> externa = new ArrayList<>();
    externa.add(item(1, StatusOrcamentoItem.EM_ABERTO));
    Orcamento o = new Orcamento(1, externa);
    externa.clear();
    assertThat(o.getItens()).hasSize(1);
  }

  @Test
  void adicionarSomenteQuandoAberto() {
    Orcamento o = new Orcamento(1, new ArrayList<>());
    o.adicionar(item(1, StatusOrcamentoItem.EM_ABERTO));
    assertThat(o.getItens()).hasSize(1);
    ItemOrcamento finalizado = item(2, StatusOrcamentoItem.FINALIZADO);
    assertThatThrownBy(() -> o.adicionar(finalizado)).isInstanceOf(BusinessException.class);
  }

  @Test
  void adicionarFalhaQuandoOrcamentoNaoAberto() {
    Orcamento o = new Orcamento(1, List.of(item(1, StatusOrcamentoItem.FINALIZADO)));
    ItemOrcamento novo = item(2, StatusOrcamentoItem.EM_ABERTO);
    assertThatThrownBy(() -> o.adicionar(novo)).isInstanceOf(BusinessException.class);
  }

  @Test
  void finalizarDeveExigirItens() {
    Orcamento vazio = new Orcamento(1, new ArrayList<>());
    assertThatThrownBy(vazio::finalizar).isInstanceOf(BusinessException.class);
  }

  @Test
  void finalizarEstadoTerminal() {
    Orcamento o = new Orcamento(1, List.of(item(1, StatusOrcamentoItem.EM_ABERTO)));
    o.finalizar();
    assertThat(o.status()).isEqualTo(StatusOrcamentoItem.FINALIZADO);
  }

  @Test
  void cancelarTodosOsItens() {
    Orcamento o =
        new Orcamento(
            1,
            List.of(
                item(1, StatusOrcamentoItem.EM_ABERTO), item(2, StatusOrcamentoItem.EM_ABERTO)));
    o.cancelar();
    assertThat(o.status()).isEqualTo(StatusOrcamentoItem.CANCELADO);
    assertThat(o.valorTotalNaoCancelado()).isEqualTo(Dinheiro.ZERO);
  }
}
