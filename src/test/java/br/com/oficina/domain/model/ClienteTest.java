package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClienteTest {

  private static Documento doc() {
    return Documento.de("529.982.247-25");
  }

  @Test
  void criaClienteAtivo() {
    Cliente c = Cliente.criar("João", doc(), "j@x.com", "99");
    assertThat(c.isAtivo()).isTrue();
    assertThat(c.getNome()).isEqualTo("João");
    assertThat(c.getDocumento()).isEqualTo(doc());
    assertThat(c.getEmail()).isEqualTo("j@x.com");
    assertThat(c.getTelefone()).isEqualTo("99");
    assertThat(c.getVersao()).isZero();
    assertThat(c.getCriadoEm()).isEqualTo(c.getAtualizadoEm());
    c.definirId(10L);
    assertThat(c.getIdCliente()).isEqualTo(10L);
  }

  @Test
  void atualizarMudaAtributos() {
    Cliente c = Cliente.criar("A", doc(), null, null);
    c.atualizar("B", "b@x.com", "2");
    assertThat(c.getNome()).isEqualTo("B");
    assertThat(c.getEmail()).isEqualTo("b@x.com");
    assertThat(c.getTelefone()).isEqualTo("2");
  }

  @Test
  void desativarMarcaInativo() {
    Cliente c = Cliente.criar("A", doc(), null, null);
    c.desativar();
    assertThat(c.isAtivo()).isFalse();
    assertThatThrownBy(c::desativar).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaNomeInvalido() {
    Documento documento = doc();
    assertThatThrownBy(() -> Cliente.criar(" ", documento, null, null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaDocumentoNulo() {
    assertThatThrownBy(() -> Cliente.criar("A", null, null, null))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void reconstituiComTimestamps() {
    Instant t1 = Instant.parse("2025-01-01T10:00:00Z");
    Instant t2 = Instant.parse("2025-02-01T10:00:00Z");
    Cliente c = new Cliente(1L, "A", doc(), null, null, true, 3L, t1, t2);
    assertThat(c.getCriadoEm()).isEqualTo(t1);
    assertThat(c.getAtualizadoEm()).isEqualTo(t2);
    assertThat(c.getVersao()).isEqualTo(3L);
    Cliente c2 = new Cliente(null, "A", doc(), null, null, true, 0L, null, null);
    assertThat(c2.getCriadoEm()).isEqualTo(c2.getAtualizadoEm());
  }
}
