package br.com.oficina.adapter.persistence;

import br.com.oficina.usecase.gateway.MensagemProcessadaRepository;
import br.com.oficina.usecase.saga.MensagemSaga;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class JpaMensagemProcessadaRepository implements MensagemProcessadaRepository {

  private final SpringDataMensagemProcessadaRepository repository;

  public JpaMensagemProcessadaRepository(SpringDataMensagemProcessadaRepository repository) {
    this.repository = repository;
  }

  @Override
  public boolean registrarSeNova(MensagemSaga mensagem) {
    if (repository.existsById(mensagem.messageId())) {
      return false;
    }
    repository.saveAndFlush(
        new MensagemProcessadaJpaEntity(
            mensagem.messageId(),
            mensagem.tipo().name(),
            mensagem.numeroOs(),
            mensagem.correlationId(),
            Instant.now()));
    return true;
  }
}
