package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ServicoTest {

  @Test
  void cria() {
    Servico s = Servico.criar("Troca de óleo", "desc", Dinheiro.de("100"));
    assertThat(s.isAtivo()).isTrue();
    assertThat(s.getNome()).isEqualTo("Troca de óleo");
    assertThat(s.getDescricao()).isEqualTo("desc");
    assertThat(s.getPrecoBase().valor()).isEqualByComparingTo("100.00");
    s.definirId(5L);
    assertThat(s.getIdServico()).isEqualTo(5L);
  }

  @Test
  void atualizaEDesativa() {
    Servico s = Servico.criar("A", null, Dinheiro.de("10"));
    s.atualizar("B", "nova", Dinheiro.de("20"));
    assertThat(s.getNome()).isEqualTo("B");
    s.desativar();
    assertThat(s.isAtivo()).isFalse();
    assertThatThrownBy(s::desativar).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaInvalidos() {
    Dinheiro umReal = Dinheiro.de("1");
    assertThatThrownBy(() -> Servico.criar(" ", null, umReal))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> Servico.criar("A", null, null)).isInstanceOf(BusinessException.class);
  }

  @Test
  void reconstituiComTimestamps() {
    Instant t1 = Instant.parse("2025-01-01T10:00:00Z");
    Instant t2 = Instant.parse("2025-02-01T10:00:00Z");
    Servico s = new Servico(1L, "A", "d", Dinheiro.de("10"), true, 2L, t1, t2);
    assertThat(s.getCriadoEm()).isEqualTo(t1);
    assertThat(s.getAtualizadoEm()).isEqualTo(t2);
    assertThat(s.getVersao()).isEqualTo(2L);
    Servico s2 = new Servico(1L, "A", null, Dinheiro.de("10"), true, 0L, null, null);
    assertThat(s2.getCriadoEm()).isEqualTo(s2.getAtualizadoEm());
  }
}
