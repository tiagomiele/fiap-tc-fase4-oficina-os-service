package br.com.oficina.adapter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtTokenService internalTokens;
  private final ServerlessJwtVerifier serverlessTokens;

  public JwtAuthenticationFilter(
      JwtTokenService internalTokens, ServerlessJwtVerifier serverlessTokens) {
    this.internalTokens = internalTokens;
    this.serverlessTokens = serverlessTokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String auth = request.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer ")) {
      try {
        AuthenticatedPrincipal principal = authenticate(auth.substring(7));
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (RuntimeException exception) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }

  private AuthenticatedPrincipal authenticate(String token) {
    try {
      Claims claims = internalTokens.parse(token);
      var role = internalTokens.papelDe(claims);
      UUID.fromString(claims.getSubject());
      return new AuthenticatedPrincipal(
          claims.getSubject(), claims.get("email", String.class), role.name(), null);
    } catch (JwtException | IllegalArgumentException exception) {
      return serverlessTokens.verify(token);
    }
  }
}
