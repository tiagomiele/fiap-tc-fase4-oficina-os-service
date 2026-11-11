package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.util.Objects;

/** Value Object para CPF/CNPJ com validação de dígitos. */
public final class Documento {

  private static final String ERRO_DOCUMENTO_INVALIDO = "DOCUMENTO_INVALIDO";

  public enum Tipo {
    CPF,
    CNPJ
  }

  private final String valor;
  private final Tipo tipo;

  private Documento(String valor, Tipo tipo) {
    this.valor = valor;
    this.tipo = tipo;
  }

  public static Documento de(String raw) {
    if (raw == null) {
      throw new BusinessException(ERRO_DOCUMENTO_INVALIDO, "Documento não informado");
    }
    String somenteDigitos = raw.replaceAll("\\D", "");
    if (somenteDigitos.length() == 11) {
      if (!cpfValido(somenteDigitos)) {
        throw new BusinessException(ERRO_DOCUMENTO_INVALIDO, "CPF inválido");
      }
      return new Documento(somenteDigitos, Tipo.CPF);
    }
    if (somenteDigitos.length() == 14) {
      if (!cnpjValido(somenteDigitos)) {
        throw new BusinessException(ERRO_DOCUMENTO_INVALIDO, "CNPJ inválido");
      }
      return new Documento(somenteDigitos, Tipo.CNPJ);
    }
    throw new BusinessException(
        "DOCUMENTO_INVALIDO", "Documento deve ter 11 (CPF) ou 14 (CNPJ) dígitos");
  }

  public String valor() {
    return valor;
  }

  public Tipo tipo() {
    return tipo;
  }

  private static boolean cpfValido(String cpf) {
    if (cpf.chars().distinct().count() == 1) return false;
    int[] d = cpf.chars().map(c -> c - '0').toArray();
    int s1 = 0;
    for (int i = 0; i < 9; i++) s1 += d[i] * (10 - i);
    int v1 = (s1 * 10) % 11;
    if (v1 == 10) v1 = 0;
    if (v1 != d[9]) return false;
    int s2 = 0;
    for (int i = 0; i < 10; i++) s2 += d[i] * (11 - i);
    int v2 = (s2 * 10) % 11;
    if (v2 == 10) v2 = 0;
    return v2 == d[10];
  }

  private static boolean cnpjValido(String cnpj) {
    if (cnpj.chars().distinct().count() == 1) return false;
    int[] d = cnpj.chars().map(c -> c - '0').toArray();
    int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    int s1 = 0;
    for (int i = 0; i < 12; i++) s1 += d[i] * p1[i];
    int v1 = s1 % 11 < 2 ? 0 : 11 - (s1 % 11);
    if (v1 != d[12]) return false;
    int s2 = 0;
    for (int i = 0; i < 13; i++) s2 += d[i] * p2[i];
    int v2 = s2 % 11 < 2 ? 0 : 11 - (s2 % 11);
    return v2 == d[13];
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Documento d && valor.equals(d.valor);
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
