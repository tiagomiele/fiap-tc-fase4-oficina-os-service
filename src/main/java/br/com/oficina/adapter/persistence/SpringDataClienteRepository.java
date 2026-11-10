package br.com.oficina.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataClienteRepository extends JpaRepository<ClienteJpaEntity, Long> {
  Optional<ClienteJpaEntity> findByDocumento(String documento);

  boolean existsByDocumento(String documento);
}
