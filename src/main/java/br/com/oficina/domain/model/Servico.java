package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;

public class Servico {

  private Long idServico;
  private String nome;
  private String descricao;
  private Dinheiro precoBase;
  private boolean ativo;
  private long versao;
  private final Instant criadoEm;
  private Instant atualizadoEm;

  @SuppressWarnings("java:S107") // Reconstituição de agregado DDD requer todos os atributos.
  public Servico(
      Long idServico,
      String nome,
      String descricao,
      Dinheiro precoBase,
      boolean ativo,
      long versao,
      Instant criadoEm,
      Instant atualizadoEm) {
    this.idServico = idServico;
    this.nome = validarNome(nome);
    this.descricao = descricao;
    this.precoBase = validarPreco(precoBase);
    this.ativo = ativo;
    this.versao = versao;
    this.criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    this.atualizadoEm = atualizadoEm == null ? this.criadoEm : atualizadoEm;
  }

  public static Servico criar(String nome, String descricao, Dinheiro precoBase) {
    Instant agora = Instant.now();
    return new Servico(null, nome, descricao, precoBase, true, 0L, agora, agora);
  }

  public void atualizar(String nome, String descricao, Dinheiro precoBase) {
    this.nome = validarNome(nome);
    this.descricao = descricao;
    this.precoBase = validarPreco(precoBase);
    this.atualizadoEm = Instant.now();
  }

  public void desativar() {
    if (!ativo) {
      throw new BusinessException("SERVICO_JA_INATIVO", "Serviço já está inativo");
    }
    this.ativo = false;
    this.atualizadoEm = Instant.now();
  }

  private static String validarNome(String n) {
    if (n == null || n.isBlank()) {
      throw new BusinessException("SERVICO_INVALIDO", "Nome do serviço é obrigatório");
    }
    return n.trim();
  }

  private static Dinheiro validarPreco(Dinheiro p) {
    if (p == null) {
      throw new BusinessException("SERVICO_INVALIDO", "Preço base é obrigatório");
    }
    return p;
  }

  public Long getIdServico() {
    return idServico;
  }

  public void definirId(Long id) {
    this.idServico = id;
  }

  public String getNome() {
    return nome;
  }

  public String getDescricao() {
    return descricao;
  }

  public Dinheiro getPrecoBase() {
    return precoBase;
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
