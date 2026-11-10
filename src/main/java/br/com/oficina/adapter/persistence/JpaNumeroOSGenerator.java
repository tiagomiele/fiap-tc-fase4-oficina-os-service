package br.com.oficina.adapter.persistence;

import br.com.oficina.domain.model.NumeroOS;
import br.com.oficina.usecase.gateway.NumeroOSGenerator;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaNumeroOSGenerator implements NumeroOSGenerator {

  private final SpringDataNumeroOSSequenciaRepository repo;

  public JpaNumeroOSGenerator(SpringDataNumeroOSSequenciaRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  public NumeroOS proximo() {
    LocalDate hoje = LocalDate.now();
    int mes = hoje.getMonthValue();
    int ano = hoje.getYear();
    repo.inicializarSeAusente(mes, ano);
    NumeroOSSequenciaJpaEntity seq =
        repo.lockByMesAno(mes, ano)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Sequência de número de OS não encontrada após inicialização: "
                            + mes
                            + "/"
                            + ano));
    int proximo = seq.getUltimoNumero() + 1;
    seq.setUltimoNumero(proximo);
    repo.save(seq);
    return NumeroOS.gerar(mes, ano, proximo);
  }
}
