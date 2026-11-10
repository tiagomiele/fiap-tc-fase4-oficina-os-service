package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import java.time.Year;

public class Veiculo {

  private final VeiculoId id;
  private String marca;
  private String modelo;
  private int ano;
  private boolean ativo;
  private long versao;
  private final Instant criadoEm;
  private Instant atualizadoEm;

  @SuppressWarnings("java:S107") // Reconstituição de agregado DDD requer todos os atributos.
  public Veiculo(
      VeiculoId id,
      String marca,
      String modelo,
      int ano,
      boolean ativo,
      long versao,
      Instant criadoEm,
      Instant atualizadoEm) {
    this.id = id;
    this.marca = validar("marca", marca);
    this.modelo = validar("modelo", modelo);
    this.ano = validarAno(ano);
    this.ativo = ativo;
    this.versao = versao;
    this.criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    this.atualizadoEm = atualizadoEm == null ? this.criadoEm : atualizadoEm;
  }

  public static Veiculo criar(Placa placa, Long idCliente, String marca, String modelo, int ano) {
    Instant agora = Instant.now();
    return new Veiculo(new VeiculoId(placa, idCliente), marca, modelo, ano, true, 0L, agora, agora);
  }

  public void atualizar(String marca, String modelo, int ano) {
    this.marca = validar("marca", marca);
    this.modelo = validar("modelo", modelo);
    this.ano = validarAno(ano);
    this.atualizadoEm = Instant.now();
  }

  public void desativar() {
    if (!ativo) {
      throw new BusinessException("VEICULO_JA_INATIVO", "Veículo já está inativo");
    }
    this.ativo = false;
    this.atualizadoEm = Instant.now();
  }

  private static String validar(String campo, String valor) {
    if (valor == null || valor.isBlank()) {
      throw new BusinessException("VEICULO_INVALIDO", campo + " é obrigatório");
    }
    return valor.trim();
  }

  private static int validarAno(int ano) {
    int atual = Year.now().getValue();
    if (ano < 1900 || ano > atual + 1) {
      throw new BusinessException("VEICULO_INVALIDO", "Ano inválido");
    }
    return ano;
  }

  public VeiculoId getId() {
    return id;
  }

  public String getMarca() {
    return marca;
  }

  public String getModelo() {
    return modelo;
  }

  public int getAno() {
    return ano;
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
