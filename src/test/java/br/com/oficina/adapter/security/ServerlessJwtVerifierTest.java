package br.com.oficina.adapter.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jsonwebtoken.Jwts;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

class ServerlessJwtVerifierTest {

  @Test
  void verifiesRsaClientToken() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    Instant now = Instant.now();
    String token =
        Jwts.builder()
            .subject("42")
            .claim("client_id", 42L)
            .claim("role", "CLIENTE")
            .claim("aud", "oficina-backend")
            .issuer("fiap-tc-fase4-oficina-auth-serverless")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(900)))
            .signWith(pair.getPrivate(), Jwts.SIG.RS256)
            .compact();
    String publicKey =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(pair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";

    var principal =
        new ServerlessJwtVerifier(
                new ServerlessJwtProperties(
                    publicKey, "fiap-tc-fase4-oficina-auth-serverless", "oficina-backend"))
            .verify(token);

    assertEquals("CLIENTE", principal.role());
    assertEquals(42L, principal.clientId());
  }
}
