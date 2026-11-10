package br.com.oficina.infrastructure.config;

import br.com.oficina.domain.enums.Papel;
import br.com.oficina.domain.model.User;
import br.com.oficina.usecase.gateway.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final String email;
  private final String senha;

  public AdminBootstrap(
      UserRepository users,
      PasswordEncoder encoder,
      @Value("${oficina.bootstrap.admin.email:admin@oficina.local}") String email,
      @Value("${oficina.bootstrap.admin.password:admin123}") String senha) {
    this.users = users;
    this.encoder = encoder;
    this.email = email;
    this.senha = senha;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void sincronizarAdmin() {
    User admin =
        users
            .porEmail(email)
            .orElseGet(
                () -> {
                  User novo =
                      User.criar(email, encoder.encode(senha), Papel.FUNCIONARIO_DA_OFICINA);
                  users.salvar(novo);
                  log.info("Usuário admin criado: {}", email);
                  return novo;
                });
    if (!encoder.matches(senha, admin.getSenhaHash())) {
      admin.trocarSenha(encoder.encode(senha));
      users.salvar(admin);
      log.info("Senha do usuário admin sincronizada com a configuração: {}", email);
    }
  }
}
