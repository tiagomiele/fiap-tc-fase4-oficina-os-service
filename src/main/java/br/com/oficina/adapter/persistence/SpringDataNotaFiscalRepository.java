package br.com.oficina.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataNotaFiscalRepository
    extends JpaRepository<NotaFiscalFornecedorJpaEntity, NotaFiscalFornecedorIdJpa> {}
