package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.util.Objects;
import java.util.regex.Pattern;

/** Value Object para placa de veículo (formato antigo ou Mercosul). */
public final class Placa {

  private static final Pattern ANTIGA = Pattern.compile("^[A-Z]{3}\\d{4}$");
  private static final Pattern MERCOSUL = Pattern.compile("^[A-Z]{3}\\d[A-Z]\\d{2}$");

  private final String valor;

  private Placa(String valor) {
    this.valor = valor;
  }

  public static Placa de(String raw) {
    if (raw == null) {
      throw new BusinessException("PLACA_INVALIDA", "Placa não informada");
    }
    String normalizada = raw.trim().toUpperCase().replace("-", "").replace(" ", "");
    if (!ANTIGA.matcher(normalizada).matches() && !MERCOSUL.matcher(normalizada).matches()) {
      throw new BusinessException("PLACA_INVALIDA", "Placa inválida: " + raw);
    }
    return new Placa(normalizada);
  }

  public String valor() {
    return valor;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Placa p && valor.equals(p.valor);
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
