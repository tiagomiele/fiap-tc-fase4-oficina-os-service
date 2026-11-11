package br.com.oficina.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.oficina.domain.enums.Papel;
import br.com.oficina.domain.model.User;
import br.com.oficina.usecase.gateway.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapTest {

  private static final String EMAIL = "admin@oficina.local";

  private final PasswordEncoder encoder = new BCryptPasswordEncoder();
  private final MemoriaUserRepository users = new MemoriaUserRepository();

  @Test
  void criaAdminQuandoAusente() {
    new AdminBootstrap(users, encoder, EMAIL, "senha-nova-123").sincronizarAdmin();

    User admin = users.porEmail(EMAIL).orElseThrow();
    assertThat(admin.getPapel()).isEqualTo(Papel.FUNCIONARIO_DA_OFICINA);
    assertThat(encoder.matches("senha-nova-123", admin.getSenhaHash())).isTrue();
  }

  @Test
  void sincronizaSenhaQuandoConfiguracaoMuda() {
    users.salvar(
        User.criar(EMAIL, encoder.encode("senha-antiga-123"), Papel.FUNCIONARIO_DA_OFICINA));

    new AdminBootstrap(users, encoder, EMAIL, "senha-nova-123").sincronizarAdmin();

    User admin = users.porEmail(EMAIL).orElseThrow();
    assertThat(encoder.matches("senha-nova-123", admin.getSenhaHash())).isTrue();
    assertThat(users.gravacoes).isEqualTo(2);
  }

  @Test
  void naoRegravaQuandoSenhaJaCorresponde() {
    users.salvar(User.criar(EMAIL, encoder.encode("senha-nova-123"), Papel.FUNCIONARIO_DA_OFICINA));

    new AdminBootstrap(users, encoder, EMAIL, "senha-nova-123").sincronizarAdmin();

    assertThat(users.gravacoes).isEqualTo(1);
  }

  private static final class MemoriaUserRepository implements UserRepository {
    private final Map<String, User> porEmail = new HashMap<>();
    private int gravacoes;

    @Override
    public User salvar(User user) {
      gravacoes++;
      porEmail.put(user.getEmail(), user);
      return user;
    }

    @Override
    public Optional<User> porId(UUID id) {
      return porEmail.values().stream().filter(u -> u.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<User> porEmail(String email) {
      return Optional.ofNullable(porEmail.get(email));
    }

    @Override
    public boolean existePorEmail(String email) {
      return porEmail.containsKey(email);
    }
  }
}
