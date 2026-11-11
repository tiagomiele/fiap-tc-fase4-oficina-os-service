package br.com.oficina.usecase.saga;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Envelope JSON trafegado nas filas SQS da Saga. */
public record MensagemSaga(
    String messageId,
    TipoMensagemSaga tipo,
    String correlationId,
    String numeroOs,
    String origem,
    Instant ocorridoEm,
    Map<String, Object> payload) {

  public static final String ORIGEM_OS_SERVICE = "fiap-tc-fase4-oficina-os-service";

  public MensagemSaga {
    Objects.requireNonNull(messageId, "messageId é obrigatório");
    Objects.requireNonNull(tipo, "tipo é obrigatório");
    Objects.requireNonNull(numeroOs, "numeroOs é obrigatório");
    payload =
        payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
  }

  public static MensagemSaga comando(
      TipoMensagemSaga tipo, String numeroOs, Map<String, Object> payload) {
    return new MensagemSaga(
        UUID.randomUUID().toString(),
        tipo,
        null,
        numeroOs,
        ORIGEM_OS_SERVICE,
        Instant.now(),
        payload);
  }

  /** Monta o payload a partir de pares chave/valor, ignorando valores nulos. */
  public static Map<String, Object> dados(Object... chaveValor) {
    if (chaveValor.length % 2 != 0) {
      throw new IllegalArgumentException("Informe pares chave/valor");
    }
    Map<String, Object> dados = new LinkedHashMap<>();
    for (int i = 0; i < chaveValor.length; i += 2) {
      if (chaveValor[i + 1] != null) {
        dados.put(String.valueOf(chaveValor[i]), chaveValor[i + 1]);
      }
    }
    return dados;
  }

  public MensagemSaga comCorrelationId(String novoCorrelationId) {
    return new MensagemSaga(
        messageId, tipo, novoCorrelationId, numeroOs, origem, ocorridoEm, payload);
  }

  public String texto(String chave) {
    Object valor = payload.get(chave);
    return valor == null ? null : valor.toString();
  }
}
