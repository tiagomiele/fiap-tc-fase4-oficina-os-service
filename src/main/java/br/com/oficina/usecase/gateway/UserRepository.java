package br.com.oficina.usecase.gateway;

import br.com.oficina.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
  User salvar(User user);

  Optional<User> porId(UUID id);

  Optional<User> porEmail(String email);

  boolean existePorEmail(String email);
}
