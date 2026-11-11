package br.com.oficina.domain.model;

import br.com.oficina.domain.enums.StatusOrdemServico;
import java.time.Instant;

public record HistoricoStatusOrdemServico(
    StatusOrdemServico status,
    Instant entradaEm,
    Instant saidaEm,
    Long duracaoMilisegundos,
    String correlationId) {}
