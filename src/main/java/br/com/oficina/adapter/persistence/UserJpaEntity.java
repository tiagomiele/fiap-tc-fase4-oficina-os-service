package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.enums.Papel;
import br.com.oficina.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserJpaEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "senha_hash", nullable = false)
  private String senhaHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Papel papel;

  @Column(nullable = false)
  private boolean ativo;

  @Version
  @Column(nullable = false)
  private Long versao;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private Instant atualizadoEm;

  protected UserJpaEntity() {}

  public UserJpaEntity(User u) {
    this.id = u.getId();
    this.email = u.getEmail();
    this.senhaHash = u.getSenhaHash();
    this.papel = u.getPapel();
    this.ativo = u.isAtivo();
    this.versao = u.getVersao();
    this.criadoEm = u.getCriadoEm();
    this.atualizadoEm = u.getAtualizadoEm();
  }

  public User toDomain() {
    return new User(
        id, email, senhaHash, papel, ativo, versao == null ? 0L : versao, criadoEm, atualizadoEm);
  }

  public void atualizarDe(User u) {
    this.email = u.getEmail();
    this.senhaHash = u.getSenhaHash();
    this.papel = u.getPapel();
    this.ativo = u.isAtivo();
    this.atualizadoEm = u.getAtualizadoEm();
  }

  public UUID getId() {
    return id;
  }
}
