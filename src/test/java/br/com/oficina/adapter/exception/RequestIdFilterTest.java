package br.com.oficina.adapter.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void preservaRequestIdValido() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> idDuranteRequisicao = new AtomicReference<>();
    request.addHeader(RequestIdFilter.HEADER, "gateway-request_123");

    filter.doFilter(
        request, response, (req, res) -> idDuranteRequisicao.set(MDC.get(RequestIdFilter.MDC_KEY)));

    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("gateway-request_123");
    assertThat(idDuranteRequisicao).hasValue("gateway-request_123");
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void substituiRequestIdInvalidoPorUuidLimitado() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader(RequestIdFilter.HEADER, "cpf=52998224725 token=segredo");

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(RequestIdFilter.HEADER))
        .matches("[0-9a-f-]{36}")
        .hasSizeLessThanOrEqualTo(64)
        .doesNotContain("52998224725", "segredo");
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }
}
