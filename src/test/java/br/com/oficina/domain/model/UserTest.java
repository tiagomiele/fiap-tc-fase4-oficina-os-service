package br.com.oficina.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.domain.enums.Papel;
import br.com.oficina.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void criaUsuarioNormalizandoEmail() {
    User u = User.criar("AdMiN@oficina.local", "hash", Papel.FUNCIONARIO_DA_OFICINA);
    assertThat(u.getEmail()).isEqualTo("admin@oficina.local");
    assertThat(u.getSenhaHash()).isEqualTo("hash");
    assertThat(u.getPapel()).isEqualTo(Papel.FUNCIONARIO_DA_OFICINA);
    assertThat(u.isAtivo()).isTrue();
    assertThat(u.getId()).isNotNull();
    assertThat(u.getCriadoEm()).isNotNull();
    assertThat(u.getAtualizadoEm()).isNotNull();
    assertThat(u.getVersao()).isZero();
  }

  @Test
  void trocarSenhaAtualizaHash() {
    User u = User.criar("a@b.co", "h1", Papel.TECNICO_DA_OFICINA);
    u.trocarSenha("h2");
    assertThat(u.getSenhaHash()).isEqualTo("h2");
  }

  @Test
  void desativarDuasVezesFalha() {
    User u = User.criar("a@b.co", "h", Papel.TECNICO_DA_OFICINA);
    u.desativar();
    assertThat(u.isAtivo()).isFalse();
    assertThatThrownBy(u::desativar).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejeitaInvalidos() {
    assertThatThrownBy(() -> User.criar(null, "h", Papel.FUNCIONARIO_DA_OFICINA))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> User.criar("no-at", "h", Papel.FUNCIONARIO_DA_OFICINA))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> User.criar("a@b.co", " ", Papel.FUNCIONARIO_DA_OFICINA))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> User.criar("a@b.co", "h", null))
        .isInstanceOf(NullPointerException.class);
  }
}
