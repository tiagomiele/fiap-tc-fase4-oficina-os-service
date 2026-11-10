package br.com.oficina.usecase;

import br.com.oficina.domain.exception.BusinessException;
import br.com.oficina.domain.model.User;
import br.com.oficina.usecase.gateway.TokenGateway;
import br.com.oficina.usecase.gateway.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl {

  public record Resultado(String accessToken, long expiresIn, String papel, String email) {}

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final TokenGateway tokens;

  public AuthServiceImpl(UserRepository users, PasswordEncoder encoder, TokenGateway tokens) {
    this.users = users;
    this.encoder = encoder;
    this.tokens = tokens;
  }

  public Resultado executar(String email, String senha) {
    return executar(email, senha, null);
  }

  /**
   * Autentica o usuário e emite um JWT. Se {@code validadeMinutos} for informado, o token tem essa
   * validade em minutos (limites validados pelo controller: 1–1440). Se {@code null}, usa o default
   * configurado em {@code jwt.access-token-ttl-minutes}.
   */
  public Resultado executar(String email, String senha, Integer validadeMinutos) {
    User user =
        users
            .porEmail(email)
            .orElseThrow(
                () -> new BusinessException("CREDENCIAIS_INVALIDAS", "Credenciais inválidas"));
    if (!user.isAtivo() || !encoder.matches(senha, user.getSenhaHash())) {
      throw new BusinessException("CREDENCIAIS_INVALIDAS", "Credenciais inválidas");
    }
    String token;
    long expiraEm;
    if (validadeMinutos == null) {
      token = tokens.gerar(user);
      expiraEm = tokens.ttlSegundos();
    } else {
      token = tokens.gerar(user, validadeMinutos);
      expiraEm = tokens.ttlSegundos(validadeMinutos);
    }
    return new Resultado(token, expiraEm, user.getPapel().name(), user.getEmail());
  }
}
