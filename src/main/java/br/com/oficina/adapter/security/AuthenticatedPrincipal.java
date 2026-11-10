package br.com.oficina.adapter.security;

public record AuthenticatedPrincipal(String subject, String email, String role, Long clientId) {

  public boolean isClient() {
    return "CLIENTE".equals(role) && clientId != null;
  }
}
