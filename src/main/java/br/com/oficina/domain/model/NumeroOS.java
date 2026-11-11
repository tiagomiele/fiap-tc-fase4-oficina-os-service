package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.util.Objects;
import java.util.regex.Pattern;

/** Formato: OS-MMAAAA-NNNNNN (ex: OS-042026-000001). */
public final class NumeroOS {

  private static final String ERRO_NUMERO_OS_INVALIDO = "NUMERO_OS_INVALIDO";
  private static final Pattern REGEX = Pattern.compile("^OS-(\\d{2})(\\d{4})-(\\d{6})$");

  private final String valor;
  private final int mes;
  private final int ano;
  private final int sequencial;

  private NumeroOS(String valor, int mes, int ano, int sequencial) {
    this.valor = valor;
    this.mes = mes;
    this.ano = ano;
    this.sequencial = sequencial;
  }

  public static NumeroOS de(String valor) {
    if (valor == null || !REGEX.matcher(valor).matches()) {
      throw new BusinessException(
          ERRO_NUMERO_OS_INVALIDO, "Formato inválido. Esperado OS-MMAAAA-NNNNNN");
    }
    var m = REGEX.matcher(valor);
    m.matches();
    int mes = Integer.parseInt(m.group(1));
    int ano = Integer.parseInt(m.group(2));
    int seq = Integer.parseInt(m.group(3));
    if (mes < 1 || mes > 12) {
      throw new BusinessException(ERRO_NUMERO_OS_INVALIDO, "Mês inválido");
    }
    if (ano < 2020 || ano > 2100) {
      throw new BusinessException(ERRO_NUMERO_OS_INVALIDO, "Ano fora do intervalo permitido");
    }
    if (seq < 1) {
      throw new BusinessException(ERRO_NUMERO_OS_INVALIDO, "Sequencial deve ser >= 1");
    }
    return new NumeroOS(valor, mes, ano, seq);
  }

  public static NumeroOS gerar(int mes, int ano, int sequencial) {
    String valor = String.format("OS-%02d%04d-%06d", mes, ano, sequencial);
    return de(valor);
  }

  public String valor() {
    return valor;
  }

  public int mes() {
    return mes;
  }

  public int ano() {
    return ano;
  }

  public int sequencial() {
    return sequencial;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof NumeroOS n && valor.equals(n.valor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(valor);
  }

  @Override
  public String toString() {
    return valor;
  }
}
