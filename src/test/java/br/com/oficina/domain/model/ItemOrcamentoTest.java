package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.enums.StatusOrcamentoItem;
import br.com.oficina.domain.enums.TipoItem;
import br.com.oficina.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

class ItemOrcamentoTest {

  private ItemOrcamento novo() {
    return new ItemOrcamento(
        1,
        1,
        TipoItem.SERVICO,
        StatusOrcamentoItem.EM_ABERTO,
        10L,
        "Serviço",
        2,
        Dinheiro.de("50"));
  }

  @Test
  void subtotalEDados() {
    ItemOrcamento it = novo();
    assertThat(it.subtotal().valor()).isEqualByComparingTo("100.00");
    assertThat(it.getIdOrcamento()).isEqualTo(1);
    assertThat(it.getIdOrcamentoItem()).isEqualTo(1);
    assertThat(it.getTipoItem()).isEqualTo(TipoItem.SERVICO);
    assertThat(it.getStatus()).isEqualTo(StatusOrcamentoItem.EM_ABERTO);
    assertThat(it.getIdServicoSku()).isEqualTo(10L);
    assertThat(it.getDescricao()).isEqualTo("Serviço");
    assertThat(it.getQuantidade()).isEqualTo(2);
    assertThat(it.getPrecoUnitario().valor()).isEqualByComparingTo("50.00");
  }

  @Test
  void finalizarSoEmAberto() {
    ItemOrcamento it = novo();
    it.finalizar();
    assertThat(it.getStatus()).isEqualTo(StatusOrcamentoItem.FINALIZADO);
    assertThatThrownBy(it::finalizar).isInstanceOf(BusinessException.class);
  }

  @Test
  void cancelarIdempotente() {
    ItemOrcamento it = novo();
    it.cancelar();
    it.cancelar();
    assertThat(it.getStatus()).isEqualTo(StatusOrcamentoItem.CANCELADO);
  }

  @Test
  void equalsEHashCode() {
    ItemOrcamento a = novo();
    ItemOrcamento b =
        new ItemOrcamento(
            1, 1, TipoItem.PECA, StatusOrcamentoItem.EM_ABERTO, 99L, "x", 1, Dinheiro.de("1"));
    ItemOrcamento c =
        new ItemOrcamento(
            1, 2, TipoItem.SERVICO, StatusOrcamentoItem.EM_ABERTO, 10L, "y", 1, Dinheiro.de("1"));
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c).isNotEqualTo("x");
  }

  @Test
  void rejeitaInvariantes() {
    StatusOrcamentoItem ab = StatusOrcamentoItem.EM_ABERTO;
    Dinheiro um = Dinheiro.de("1");
    assertThatThrownBy(() -> new ItemOrcamento(0, 1, TipoItem.SERVICO, ab, 1L, "d", 1, um))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new ItemOrcamento(1, 0, TipoItem.SERVICO, ab, 1L, "d", 1, um))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new ItemOrcamento(1, 1, null, ab, 1L, "d", 1, um))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new ItemOrcamento(1, 1, TipoItem.PECA, null, 1L, "d", 1, um))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new ItemOrcamento(1, 1, TipoItem.PECA, ab, null, "d", 1, um))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new ItemOrcamento(1, 1, TipoItem.PECA, ab, 1L, " ", 1, um))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new ItemOrcamento(1, 1, TipoItem.PECA, ab, 1L, "d", 0, um))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new ItemOrcamento(1, 1, TipoItem.PECA, ab, 1L, "d", 1, null))
        .isInstanceOf(BusinessException.class);
  }
}
