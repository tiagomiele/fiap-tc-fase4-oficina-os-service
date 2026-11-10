package br.com.oficina.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "numero_os_sequencia")
@IdClass(NumeroOSSequenciaIdJpa.class)
public class NumeroOSSequenciaJpaEntity {

  @Id private int mes;
  @Id private int ano;

  @Column(name = "ultimo_numero", nullable = false)
  private int ultimoNumero;

  protected NumeroOSSequenciaJpaEntity() {}

  public NumeroOSSequenciaJpaEntity(int mes, int ano, int ultimoNumero) {
    this.mes = mes;
    this.ano = ano;
    this.ultimoNumero = ultimoNumero;
  }

  public int getMes() {
    return mes;
  }

  public int getAno() {
    return ano;
  }

  public int getUltimoNumero() {
    return ultimoNumero;
  }

  public void setUltimoNumero(int v) {
    this.ultimoNumero = v;
  }
}
