package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;

public class Peca {

  private Long idSku;
  private String nome;
  private Dinheiro precoVenda;
  private boolean ativo;
  private long versao;
  private final Instant criadoEm;
  private Instant atualizadoEm;

  public Peca(
      Long idSku,
      String nome,
      Dinheiro precoVenda,
      boolean ativo,
      long versao,
      Instant criadoEm,
      Instant atualizadoEm) {
    this.idSku = idSku;
    this.nome = validarNome(nome);
    this.precoVenda = validarPreco(precoVenda);
    this.ativo = ativo;
    this.versao = versao;
    this.criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    this.atualizadoEm = atualizadoEm == null ? this.criadoEm : atualizadoEm;
  }

  public static Peca criar(String nome, Dinheiro precoVenda) {
    Instant agora = Instant.now();
    return new Peca(null, nome, precoVenda, true, 0L, agora, agora);
  }

  public void atualizar(String nome, Dinheiro precoVenda) {
    this.nome = validarNome(nome);
    this.precoVenda = validarPreco(precoVenda);
    this.atualizadoEm = Instant.now();
  }

  public void desativar() {
    if (!ativo) {
      throw new BusinessException("PECA_JA_INATIVA", "Peça já está inativa");
    }
    this.ativo = false;
    this.atualizadoEm = Instant.now();
  }

  private static String validarNome(String n) {
    if (n == null || n.isBlank()) {
      throw new BusinessException("PECA_INVALIDA", "Nome da peça é obrigatório");
    }
    return n.trim();
  }

  private static Dinheiro validarPreco(Dinheiro p) {
    if (p == null) {
      throw new BusinessException("PECA_INVALIDA", "Preço é obrigatório");
    }
    return p;
  }

  public Long getIdSku() {
    return idSku;
  }

  public void definirId(Long id) {
    this.idSku = id;
  }

  public String getNome() {
    return nome;
  }

  public Dinheiro getPrecoVenda() {
    return precoVenda;
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
