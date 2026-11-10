package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotaFiscalFornecedorTest {

  private NotaFiscalFornecedorId id() {
    return new NotaFiscalFornecedorId("NF-1", "1", "00.000.000/0001-00", LocalDate.now());
  }

  @Test
  void emiteCalculaTotal() {
    var item1 = new ItemNotaFiscalFornecedor(1L, 2, Dinheiro.de("10"));
    var item2 = new ItemNotaFiscalFornecedor(2L, 3, Dinheiro.de("5"));
    NotaFiscalFornecedor nf =
        NotaFiscalFornecedor.emitir(id(), "Fornecedor ACME", List.of(item1, item2));
    assertThat(nf.getValorTotal().valor()).isEqualByComparingTo("35.00");
    assertThat(nf.getItens()).hasSize(2);
    assertThat(nf.getNomeFornecedor()).isEqualTo("Fornecedor ACME");
    assertThat(nf.isEstornada()).isFalse();
    assertThat(nf.getCriadoEm()).isNotNull();
  }

  @Test
  void estornaNaoPodeRepetir() {
    var item = new ItemNotaFiscalFornecedor(1L, 1, Dinheiro.de("1"));
    NotaFiscalFornecedor nf = NotaFiscalFornecedor.emitir(id(), "X", List.of(item));
    nf.estornar();
    assertThat(nf.isEstornada()).isTrue();
    assertThatThrownBy(nf::estornar).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaInvariantes() {
    NotaFiscalFornecedorId id = id();
    List<ItemNotaFiscalFornecedor> comItem =
        List.of(new ItemNotaFiscalFornecedor(1L, 1, Dinheiro.de("1")));
    List<ItemNotaFiscalFornecedor> vazia = List.of();
    assertThatThrownBy(() -> NotaFiscalFornecedor.emitir(id, " ", comItem))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> NotaFiscalFornecedor.emitir(id, "X", vazia))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> NotaFiscalFornecedor.emitir(id, "X", null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void itemInvalido() {
    Dinheiro um = Dinheiro.de("1");
    assertThatThrownBy(() -> new ItemNotaFiscalFornecedor(null, 1, um))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new ItemNotaFiscalFornecedor(1L, 0, um))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> new ItemNotaFiscalFornecedor(1L, 1, null))
        .isInstanceOf(BusinessException.class);
    assertThat(new ItemNotaFiscalFornecedor(1L, 2, Dinheiro.de("5")).subtotal().valor())
        .isEqualByComparingTo("10.00");
  }
}
