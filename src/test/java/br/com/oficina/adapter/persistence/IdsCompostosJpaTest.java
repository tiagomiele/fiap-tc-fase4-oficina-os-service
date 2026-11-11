package br.com.oficina.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class IdsCompostosJpaTest {

  private static final LocalDate DATA = LocalDate.of(2026, 5, 1);

  @Test
  void idNotaFiscalComparaTodosOsCampos() {
    NotaFiscalFornecedorIdJpa id = new NotaFiscalFornecedorIdJpa("1", "A", "11222333000181", DATA);
    assertThat(id)
        .isEqualTo(new NotaFiscalFornecedorIdJpa("1", "A", "11222333000181", DATA))
        .hasSameHashCodeAs(new NotaFiscalFornecedorIdJpa("1", "A", "11222333000181", DATA))
        .isNotEqualTo(new NotaFiscalFornecedorIdJpa("2", "A", "11222333000181", DATA))
        .isNotEqualTo(new NotaFiscalFornecedorIdJpa("1", "B", "11222333000181", DATA))
        .isNotEqualTo(new NotaFiscalFornecedorIdJpa("1", "A", "99999999000191", DATA))
        .isNotEqualTo(new NotaFiscalFornecedorIdJpa("1", "A", "11222333000181", DATA.plusDays(1)))
        .isNotEqualTo(null)
        .isNotEqualTo("1");
    assertThat(id.getNumeroNota()).isEqualTo("1");
    assertThat(id.getSerieNota()).isEqualTo("A");
    assertThat(id.getCnpjFornecedor()).isEqualTo("11222333000181");
    assertThat(id.getDataEmissao()).isEqualTo(DATA);
    assertThat(new NotaFiscalFornecedorIdJpa()).isEqualTo(new NotaFiscalFornecedorIdJpa());
  }

  @Test
  void idItemNotaFiscalComparaTodosOsCampos() {
    ItemNfIdJpa id = new ItemNfIdJpa("1", "A", "11222333000181", DATA, 7L);
    assertThat(id)
        .isEqualTo(new ItemNfIdJpa("1", "A", "11222333000181", DATA, 7L))
        .hasSameHashCodeAs(new ItemNfIdJpa("1", "A", "11222333000181", DATA, 7L))
        .isNotEqualTo(new ItemNfIdJpa("2", "A", "11222333000181", DATA, 7L))
        .isNotEqualTo(new ItemNfIdJpa("1", "B", "11222333000181", DATA, 7L))
        .isNotEqualTo(new ItemNfIdJpa("1", "A", "99999999000191", DATA, 7L))
        .isNotEqualTo(new ItemNfIdJpa("1", "A", "11222333000181", DATA.plusDays(1), 7L))
        .isNotEqualTo(new ItemNfIdJpa("1", "A", "11222333000181", DATA, 8L))
        .isNotEqualTo(null)
        .isNotEqualTo("1");
    assertThat(new ItemNfIdJpa()).isEqualTo(new ItemNfIdJpa());
  }
}
