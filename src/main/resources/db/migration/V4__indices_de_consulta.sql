CREATE INDEX idx_itens_orc_servico_sku
  ON orcamentos_itens_ordem_servico(id_servico_sku, tipo_item);

CREATE INDEX idx_cc_nota_fornecedor
  ON conta_corrente_oficina(numero_nota, serie_nota, cnpj_fornecedor, data_emissao);

CREATE INDEX idx_cc_tipo_data
  ON conta_corrente_oficina(tipo, data_lancamento DESC);

DROP INDEX IF EXISTS idx_cc_tipo;
