package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.User;
import br.com.oficina.usecase.gateway.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaUserRepository implements UserRepository {

  private final SpringDataUserRepository repo;

  public JpaUserRepository(SpringDataUserRepository repo) {
    this.repo = repo;
  }

  @Override
  public User salvar(User user) {
    Optional<UserJpaEntity> existente = repo.findById(user.getId());
    UserJpaEntity entity = existente.orElseGet(() -> new UserJpaEntity(user));
    if (existente.isPresent()) {
      entity.atualizarDe(user);
    }
    return repo.save(entity).toDomain();
  }

  @Override
  public Optional<User> porId(UUID id) {
    return repo.findById(id).map(UserJpaEntity::toDomain);
  }

  @Override
  public Optional<User> porEmail(String email) {
    return repo.findByEmail(email.toLowerCase()).map(UserJpaEntity::toDomain);
  }

  @Override
  public boolean existePorEmail(String email) {
    return repo.existsByEmail(email.toLowerCase());
  }
}
