package br.com.oficina.adapter.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataNumeroOSSequenciaRepository
    extends JpaRepository<NumeroOSSequenciaJpaEntity, NumeroOSSequenciaIdJpa> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM NumeroOSSequenciaJpaEntity s WHERE s.mes = :mes AND s.ano = :ano")
  Optional<NumeroOSSequenciaJpaEntity> lockByMesAno(@Param("mes") int mes, @Param("ano") int ano);

  @Modifying
  @Query(
      value =
          "INSERT INTO numero_os_sequencia(mes, ano, ultimo_numero) "
              + "VALUES (:mes, :ano, 0) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void inicializarSeAusente(@Param("mes") int mes, @Param("ano") int ano);
}
