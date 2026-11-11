package br.com.oficina.domain.model;

import br.com.oficina.domain.exception.BusinessException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NotaFiscalFornecedor {

  private final NotaFiscalFornecedorId id;
  private final String nomeFornecedor;
  private final List<ItemNotaFiscalFornecedor> itens;
  private final Dinheiro valorTotal;
  private boolean estornada;
  private final Instant criadoEm;
  private Instant atualizadoEm;

  public NotaFiscalFornecedor(
      NotaFiscalFornecedorId id,
      String nomeFornecedor,
      List<ItemNotaFiscalFornecedor> itens,
      boolean estornada,
      Instant criadoEm,
      Instant atualizadoEm) {
    this.id = Objects.requireNonNull(id);
    if (nomeFornecedor == null || nomeFornecedor.isBlank()) {
      throw new BusinessException("NF_INVALIDA", "Nome do fornecedor é obrigatório");
    }
    if (itens == null || itens.isEmpty()) {
      throw new BusinessException("NF_INVALIDA", "NF precisa de pelo menos 1 item");
    }
    this.nomeFornecedor = nomeFornecedor;
    this.itens = new ArrayList<>(itens);
    Dinheiro total = Dinheiro.ZERO;
    for (var it : itens) {
      total = total.somar(it.subtotal());
    }
    this.valorTotal = total;
    this.estornada = estornada;
    this.criadoEm = criadoEm == null ? Instant.now() : criadoEm;
    this.atualizadoEm = atualizadoEm == null ? this.criadoEm : atualizadoEm;
  }

  public static NotaFiscalFornecedor emitir(
      NotaFiscalFornecedorId id, String nomeFornecedor, List<ItemNotaFiscalFornecedor> itens) {
    Instant agora = Instant.now();
    return new NotaFiscalFornecedor(id, nomeFornecedor, itens, false, agora, agora);
  }

  public void estornar() {
    if (estornada) {
      throw new BusinessException("NF_JA_ESTORNADA", "Nota fiscal já estornada");
    }
    this.estornada = true;
    this.atualizadoEm = Instant.now();
  }

  public NotaFiscalFornecedorId getId() {
    return id;
  }

  public String getNomeFornecedor() {
    return nomeFornecedor;
  }

  public List<ItemNotaFiscalFornecedor> getItens() {
    return Collections.unmodifiableList(itens);
  }

  public Dinheiro getValorTotal() {
    return valorTotal;
  }

  public boolean isEstornada() {
    return estornada;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public Instant getAtualizadoEm() {
    return atualizadoEm;
  }
}
