package br.com.oficina.adapter.persistence;

import br.com.oficina.usecase.gateway.RelatorioGateway;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaRelatorioRepository implements RelatorioGateway {

  private final EntityManager em;

  public JpaRelatorioRepository(EntityManager em) {
    this.em = em;
  }

  @Override
  @Transactional(readOnly = true)
  @SuppressWarnings("unchecked")
  public RelatorioResult tempoMedioPorOs() {
    String sql =
        """
        SELECT id_ordem_servico,
               inicio_execucao,
               fim_execucao,
               EXTRACT(EPOCH FROM (fim_execucao - inicio_execucao)) / 3600.0 AS duracao_horas
          FROM ordens_servico
         WHERE inicio_execucao IS NOT NULL
           AND fim_execucao    IS NOT NULL
         ORDER BY fim_execucao
        """;
    List<Object[]> rows = em.createNativeQuery(sql).getResultList();
    List<OrdemExecutada> ordens =
        rows.stream()
            .map(
                r ->
                    new OrdemExecutada(
                        (String) r[0],
                        toInstant(r[1]),
                        toInstant(r[2]),
                        ((Number) r[3]).doubleValue()))
            .toList();
    double media =
        ordens.isEmpty()
            ? 0.0
            : ordens.stream().mapToDouble(OrdemExecutada::duracaoHoras).average().orElse(0.0);
    return new RelatorioResult(media, ordens.size(), ordens);
  }

  private static Instant toInstant(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Instant instant) {
      return instant;
    }
    if (value instanceof OffsetDateTime odt) {
      return odt.toInstant();
    }
    if (value instanceof Timestamp ts) {
      return ts.toInstant();
    }
    if (value instanceof LocalDateTime ldt) {
      return ldt.toInstant(ZoneOffset.UTC);
    }
    throw new IllegalStateException(
        "Tipo de coluna timestamp não suportado: " + value.getClass().getName());
  }
}
