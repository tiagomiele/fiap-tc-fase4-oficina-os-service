package br.com.oficina.domain.model;

import br.com.oficina.domain.enums.Papel;
import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class User {

  private final UUID id;
  private String email;
  private String senhaHash;
  private Papel papel;
  private boolean ativo;
  private long versao;
  private final Instant criadoEm;
  private Instant atualizadoEm;

  @SuppressWarnings("java:S107") // Reconstituição de agregado DDD requer todos os atributos.
  public User(
      UUID id,
      String email,
      String senhaHash,
      Papel papel,
      boolean ativo,
      long versao,
      Instant criadoEm,
      Instant atualizadoEm) {
    this.id = Objects.requireNonNull(id);
    this.email = validarEmail(email);
    this.senhaHash = validarSenhaHash(senhaHash);
    this.papel = Objects.requireNonNull(papel, "papel obrigatório");
    this.ativo = ativo;
    this.versao = versao;
    this.criadoEm = Objects.requireNonNull(criadoEm);
    this.atualizadoEm = Objects.requireNonNull(atualizadoEm);
  }

  public static User criar(String email, String senhaHash, Papel papel) {
    Instant agora = Instant.now();
    return new User(UUID.randomUUID(), email, senhaHash, papel, true, 0L, agora, agora);
  }

  public void desativar() {
    if (!ativo) {
      throw new BusinessException("USUARIO_JA_INATIVO", "Usuário já está inativo");
    }
    this.ativo = false;
    this.atualizadoEm = Instant.now();
  }

  public void trocarSenha(String novoHash) {
    this.senhaHash = validarSenhaHash(novoHash);
    this.atualizadoEm = Instant.now();
  }

  private static String validarEmail(String email) {
    if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw new BusinessException("EMAIL_INVALIDO", "E-mail inválido");
    }
    return email.toLowerCase();
  }

  private static String validarSenhaHash(String hash) {
    if (hash == null || hash.isBlank()) {
      throw new BusinessException("SENHA_INVALIDA", "Senha não informada");
    }
    return hash;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getSenhaHash() {
    return senhaHash;
  }

  public Papel getPapel() {
    return papel;
  }

  public boolean isAtivo() {
    return ativo;
  }

  public long getVersao() {
    return versao;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public Instant getAtualizadoEm() {
    return atualizadoEm;
  }
}
