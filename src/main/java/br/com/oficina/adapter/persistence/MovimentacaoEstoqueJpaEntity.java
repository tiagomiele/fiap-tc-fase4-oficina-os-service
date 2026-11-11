package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.enums.OrigemMovimentacao;
import br.com.oficina.domain.model.MovimentacaoEstoque;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "movimentacao_estoque_pecas")
public class MovimentacaoEstoqueJpaEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "id_sku", nullable = false)
  private Long idSku;

  @Column(nullable = false)
  private int quantidade;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OrigemMovimentacao origem;

  @Column(name = "numero_nota", length = 30)
  private String numeroNota;

  @Column(name = "serie_nota", length = 10)
  private String serieNota;

  @Column(name = "cnpj_fornecedor", length = 20)
  private String cnpjFornecedor;

  @Column(name = "data_emissao")
  private LocalDate dataEmissao;

  @Column(name = "id_ordem_servico", length = 20)
  private String idOrdemServico;

  @Column(name = "id_orcamento")
  private Integer idOrcamento;

  @Column(name = "id_orcamento_item")
  private Integer idOrcamentoItem;

  @Column(name = "data_hora", nullable = false)
  private Instant dataHora;

  protected MovimentacaoEstoqueJpaEntity() {}

  public MovimentacaoEstoqueJpaEntity(MovimentacaoEstoque m) {
    this.id = m.getId();
    this.idSku = m.getIdSku();
    this.quantidade = m.getQuantidade();
    this.origem = m.getOrigem();
    this.numeroNota = m.getNumeroNota();
    this.serieNota = m.getSerieNota();
    this.cnpjFornecedor = m.getCnpjFornecedor();
    this.dataEmissao = m.getDataEmissao();
    this.idOrdemServico = m.getIdOrdemServico();
    this.idOrcamento = m.getIdOrcamento();
    this.idOrcamentoItem = m.getIdOrcamentoItem();
    this.dataHora = m.getDataHora();
  }

  public MovimentacaoEstoque toDomain() {
    return new MovimentacaoEstoque.Builder()
        .id(id)
        .idSku(idSku)
        .quantidade(quantidade)
        .origem(origem)
        .nota(numeroNota, serieNota, cnpjFornecedor, dataEmissao)
        .orcamento(idOrdemServico, idOrcamento, idOrcamentoItem)
        .dataHora(dataHora)
        .construir();
  }

  public Long getId() {
    return id;
  }
}
