package br.com.oficina.adapter.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
    int status,
    String codigo,
    String mensagem,
    Instant timestamp,
    String path,
    String requestId,
    List<String> detalhes) {

  public ApiError(int status, String codigo, String mensagem, String path, String requestId) {
    this(status, codigo, mensagem, Instant.now(), path, requestId, List.of());
  }

  public ApiError(
      int status,
      String codigo,
      String mensagem,
      String path,
      String requestId,
      List<String> detalhes) {
    this(status, codigo, mensagem, Instant.now(), path, requestId, detalhes);
  }
}
