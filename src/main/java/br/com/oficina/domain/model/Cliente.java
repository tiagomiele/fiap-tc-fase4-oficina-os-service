package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;

public class Cliente {

  private Long idCliente;
  private String nome;
  private Documento documento;
  private String email;
  private String telefone;
  private boolean ativo;
  private long versao;
  private final Instant criadoEm;
  private Instant atualizadoEm;

  @SuppressWarnings("java:S107") // Reconstituição de agregado DDD requer todos os atributos.
  public Cliente(
      Long idCliente,
      String nome,
      Documento documento,
      String email,
      String telefone,
      boolean ativo,
      long versao,
      Instant criadoEm,
      Instant atualizadoEm) {
    this.idCliente = idCliente;
    this.nome = validarNome(nome);
    this.documento = validarDocumento(documento);
    this.email = email;
    this.telefone = telefone;
    this.ativo = ativo;
    this.versao = versao;
    this.criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    this.atualizadoEm = atualizadoEm == null ? this.criadoEm : atualizadoEm;
  }

  public static Cliente criar(String nome, Documento documento, String email, String telefone) {
    Instant agora = Instant.now();
    return new Cliente(null, nome, documento, email, telefone, true, 0L, agora, agora);
  }

  public void atualizar(String nome, String email, String telefone) {
    this.nome = validarNome(nome);
    this.email = email;
    this.telefone = telefone;
    this.atualizadoEm = Instant.now();
  }

  public void desativar() {
    if (!ativo) {
      throw new BusinessException("CLIENTE_JA_INATIVO", "Cliente já está inativo");
    }
    this.ativo = false;
    this.atualizadoEm = Instant.now();
  }

  private static String validarNome(String n) {
    if (n == null || n.isBlank()) {
      throw new BusinessException("NOME_INVALIDO", "Nome é obrigatório");
    }
    return n.trim();
  }

  private static Documento validarDocumento(Documento d) {
    if (d == null) {
      throw new BusinessException("DOCUMENTO_INVALIDO", "Documento é obrigatório");
    }
    return d;
  }

  public Long getIdCliente() {
    return idCliente;
  }

  public void definirId(Long id) {
    this.idCliente = id;
  }

  public String getNome() {
    return nome;
  }

  public Documento getDocumento() {
    return documento;
  }

  public String getEmail() {
    return email;
  }

  public String getTelefone() {
    return telefone;
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
