package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.Cliente;
import br.com.oficina.domain.model.Documento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "clientes")
public class ClienteJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_cliente")
  private Long idCliente;

  @Column(nullable = false)
  private String nome;

  @Column(nullable = false, unique = true)
  private String documento;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_documento", nullable = false, length = 4)
  private Documento.Tipo tipoDocumento;

  private String email;
  private String telefone;

  @Column(nullable = false)
  private boolean ativo;

  @Version
  @Column(nullable = false)
  private Long versao;

  @Column(name = "criado_em", nullable = false)
  private Instant criadoEm;

  @Column(name = "atualizado_em", nullable = false)
  private Instant atualizadoEm;

  protected ClienteJpaEntity() {}

  public ClienteJpaEntity(Cliente c) {
    atualizarDe(c);
    this.criadoEm = c.getCriadoEm();
  }

  public void atualizarDe(Cliente c) {
    this.idCliente = c.getIdCliente();
    this.nome = c.getNome();
    this.documento = c.getDocumento().valor();
    this.tipoDocumento = c.getDocumento().tipo();
    this.email = c.getEmail();
    this.telefone = c.getTelefone();
    this.ativo = c.isAtivo();
    this.atualizadoEm = c.getAtualizadoEm();
  }

  public Cliente toDomain() {
    return new Cliente(
        idCliente,
        nome,
        Documento.de(documento),
        email,
        telefone,
        ativo,
        versao == null ? 0L : versao,
        criadoEm,
        atualizadoEm);
  }

  public Long getIdCliente() {
    return idCliente;
  }
}
