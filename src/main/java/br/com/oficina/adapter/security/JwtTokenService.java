package br.com.oficina.adapter.security;

import br.com.oficina.domain.enums.Papel;
import br.com.oficina.domain.model.User;
import br.com.oficina.usecase.gateway.TokenGateway;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService implements TokenGateway {

  private final JwtProperties props;
  private final SecretKey key;

  public JwtTokenService(JwtProperties props) {
    this.props = props;
    this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
  }

  public String gerar(User user) {
    return gerar(user, props.accessTokenTtlMinutes());
  }

  /**
   * Gera um token JWT com validade customizada (em minutos). Usado pelo endpoint /auth/login para
   * permitir ao cliente escolher o tempo de vida do token (1–1440).
   */
  public String gerar(User user, long ttlMinutos) {
    long minutos = ttlMinutos <= 0 ? props.accessTokenTtlMinutes() : ttlMinutos;
    Instant agora = Instant.now();
    Instant expiracao = agora.plus(Duration.ofMinutes(minutos));
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("papel", user.getPapel().name())
        .issuer(props.issuer())
        .issuedAt(Date.from(agora))
        .expiration(Date.from(expiracao))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  public long ttlSegundos() {
    return Duration.ofMinutes(props.accessTokenTtlMinutes()).toSeconds();
  }

  public long ttlSegundos(long ttlMinutos) {
    long minutos = ttlMinutos <= 0 ? props.accessTokenTtlMinutes() : ttlMinutos;
    return Duration.ofMinutes(minutos).toSeconds();
  }

  public Claims parse(String token) {
    return Jwts.parser()
        .verifyWith(key)
        .requireIssuer(props.issuer())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public Papel papelDe(Claims claims) {
    return Papel.valueOf(claims.get("papel", String.class));
  }
}
