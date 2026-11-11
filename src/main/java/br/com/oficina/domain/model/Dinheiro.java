package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Value Object monetário. Escala 2, arredondamento HALF_EVEN, não-negativo. */
public final class Dinheiro {

  private static final String ERRO_VALOR_INVALIDO = "VALOR_INVALIDO";

  public static final Dinheiro ZERO = new Dinheiro(BigDecimal.ZERO.setScale(2));

  private final BigDecimal valor;

  private Dinheiro(BigDecimal valor) {
    this.valor = valor;
  }

  public static Dinheiro de(BigDecimal valor) {
    if (valor == null) {
      throw new BusinessException(ERRO_VALOR_INVALIDO, "Valor monetário não informado");
    }
    if (valor.signum() < 0) {
      throw new BusinessException(ERRO_VALOR_INVALIDO, "Valor monetário não pode ser negativo");
    }
    return new Dinheiro(valor.setScale(2, RoundingMode.HALF_EVEN));
  }

  public static Dinheiro de(String valor) {
    return de(new BigDecimal(valor));
  }

  public BigDecimal valor() {
    return valor;
  }

  public Dinheiro somar(Dinheiro outro) {
    return new Dinheiro(valor.add(outro.valor));
  }

  public Dinheiro multiplicar(int quantidade) {
    if (quantidade < 0) {
      throw new BusinessException(ERRO_VALOR_INVALIDO, "Quantidade não pode ser negativa");
    }
    return new Dinheiro(
        valor.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_EVEN));
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Dinheiro d && valor.compareTo(d.valor) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(valor.stripTrailingZeros());
  }

  @Override
  public String toString() {
    return valor.toPlainString();
  }
}
