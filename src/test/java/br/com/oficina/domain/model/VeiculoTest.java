package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import java.time.Year;
import org.junit.jupiter.api.Test;

class VeiculoTest {

  @Test
  void cria() {
    Veiculo v = Veiculo.criar(Placa.de("ABC1234"), 1L, "Fiat", "Uno", 2020);
    assertThat(v.isAtivo()).isTrue();
    assertThat(v.getMarca()).isEqualTo("Fiat");
    assertThat(v.getModelo()).isEqualTo("Uno");
    assertThat(v.getAno()).isEqualTo(2020);
    assertThat(v.getVersao()).isZero();
    assertThat(v.getId().idCliente()).isEqualTo(1L);
  }

  @Test
  void atualiza() {
    Veiculo v = Veiculo.criar(Placa.de("ABC1234"), 1L, "A", "B", 2010);
    v.atualizar("Ford", "Ka", 2015);
    assertThat(v.getMarca()).isEqualTo("Ford");
    assertThat(v.getModelo()).isEqualTo("Ka");
    assertThat(v.getAno()).isEqualTo(2015);
  }

  @Test
  void desativa() {
    Veiculo v = Veiculo.criar(Placa.de("ABC1234"), 1L, "A", "B", 2010);
    v.desativar();
    assertThat(v.isAtivo()).isFalse();
    assertThatThrownBy(v::desativar).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaCamposInvalidos() {
    Placa p = Placa.de("ABC1234");
    int anoFuturo = Year.now().getValue() + 2;
    assertThatThrownBy(() -> Veiculo.criar(p, 1L, " ", "B", 2010))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> Veiculo.criar(p, 1L, "A", " ", 2010))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> Veiculo.criar(p, 1L, "A", "B", 1800))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> Veiculo.criar(p, 1L, "A", "B", anoFuturo))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void veiculoId() {
    VeiculoId id1 = new VeiculoId(Placa.de("ABC1234"), 1L);
    VeiculoId id2 = new VeiculoId(Placa.de("ABC1234"), 1L);
    assertThat(id1).isEqualTo(id2).hasSameHashCodeAs(id2);
  }

  @Test
  void reconstituiComTimestamps() {
    Instant t1 = Instant.parse("2025-01-01T10:00:00Z");
    Instant t2 = Instant.parse("2025-02-01T10:00:00Z");
    VeiculoId id = new VeiculoId(Placa.de("ABC1234"), 1L);
    Veiculo v = new Veiculo(id, "Fiat", "Uno", 2020, true, 5L, t1, t2);
    assertThat(v.getCriadoEm()).isEqualTo(t1);
    assertThat(v.getAtualizadoEm()).isEqualTo(t2);
    assertThat(v.getVersao()).isEqualTo(5L);
    Veiculo v2 = new Veiculo(id, "Fiat", "Uno", 2020, true, 0L, null, null);
    assertThat(v2.getCriadoEm()).isEqualTo(v2.getAtualizadoEm());
  }
}
