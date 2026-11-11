package br.com.oficina.usecase;

import br.com.oficina.domain.enums.Papel;
import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.model.User;
import br.com.oficina.usecase.gateway.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl {

  private final UserRepository users;
  private final PasswordEncoder encoder;

  public UserServiceImpl(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  public User executar(String email, String senha, Papel papel) {
    if (users.existePorEmail(email)) {
      throw new BusinessException("USUARIO_DUPLICADO", "E-mail já cadastrado");
    }
    if (senha == null || senha.length() < 8) {
      throw new BusinessException("SENHA_INVALIDA", "Senha deve ter pelo menos 8 caracteres");
    }
    return users.salvar(User.criar(email, encoder.encode(senha), papel));
  }
}
