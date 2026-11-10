package br.com.oficina.adapter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class ServerlessJwtVerifier {

  private final ServerlessJwtProperties properties;
  private volatile PublicKey publicKey;

  public ServerlessJwtVerifier(ServerlessJwtProperties properties) {
    this.properties = properties;
  }

  public AuthenticatedPrincipal verify(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(publicKey())
            .requireIssuer(properties.issuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    if (!hasAudience(claims.get("aud"), properties.audience())) {
      throw new IllegalArgumentException("Audiência JWT inválida");
    }
    if (!"CLIENTE".equals(claims.get("role", String.class))) {
      throw new IllegalArgumentException("Role JWT inválida");
    }
    Object rawClientId = claims.get("client_id");
    if (!(rawClientId instanceof Number number)) {
      throw new IllegalArgumentException("client_id JWT inválido");
    }
    long clientId = number.longValue();
    if (!Long.toString(clientId).equals(claims.getSubject())) {
      throw new IllegalArgumentException("Subject JWT inválido");
    }
    return new AuthenticatedPrincipal(claims.getSubject(), null, "CLIENTE", clientId);
  }

  private PublicKey publicKey() {
    if (publicKey == null) {
      synchronized (this) {
        if (publicKey == null) {
          publicKey = parsePublicKey(properties.publicKey());
        }
      }
    }
    return publicKey;
  }

  private static boolean hasAudience(Object audience, String expected) {
    if (audience instanceof String value) {
      return expected.equals(value);
    }
    if (audience instanceof Collection<?> values) {
      return values.contains(expected);
    }
    return false;
  }

  private static PublicKey parsePublicKey(String pem) {
    if (pem == null || pem.isBlank()) {
      throw new IllegalStateException("SERVERLESS_JWT_PUBLIC_KEY não configurada");
    }
    try {
      String content =
          pem.replace("\\n", "\n")
              .replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replaceAll("\\s", "");
      byte[] encoded = Base64.getDecoder().decode(content);
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    } catch (Exception exception) {
      throw new IllegalArgumentException("Chave pública serverless inválida", exception);
    }
  }
}
