package br.com.oficina.infrastructure.config;

import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Garante a ordem dos endpoints no Swagger UI conforme o prefixo numérico do
 * {@code @Operation(summary)}:
 *
 * <ul>
 *   <li>{@link OpenApiCustomizer} reordena o {@link Paths} server-side (afeta também Postman/SDK
 *       gerado a partir de {@code /v3/api-docs}).
 *   <li>{@link SwaggerIndexPageTransformer} customizado injeta uma função JS real para {@code
 *       operationsSorter} no {@code swagger-initializer.js} — o valor padrão do Springdoc só aceita
 *       strings ("alpha"/"method"), e uma função literal seria serializada como string e ignorada
 *       pelo Swagger UI.
 * </ul>
 */
@Configuration
public class SwaggerOrderConfig {

  static final String SORTER_SENTINEL = "__OFICINA_OPERATIONS_SORTER__";

  /**
   * Função JS executada pelo Swagger UI. Os parâmetros {@code a} e {@code b} são Maps Immutable.js
   * com estrutura {@code {path, method, operation, id}} — o {@code summary} fica em {@code
   * operation.summary}, por isso o uso de {@code getIn(['operation','summary'])}.
   */
  private static final String SORTER_FUNCTION =
      "function(a,b){return (a.getIn(['operation','summary'])||'')"
          + ".localeCompare(b.getIn(['operation','summary'])||'',undefined,{numeric:true});}";

  /** Reordena {@code paths} pelo menor prefixo numérico do {@code summary}. */
  @Bean
  public OpenApiCustomizer ordenarPathsPorSummary() {
    return openApi -> {
      Paths originais = openApi.getPaths();
      if (originais == null || originais.isEmpty()) {
        return;
      }
      List<Map.Entry<String, PathItem>> entradas = new ArrayList<>(originais.entrySet());
      entradas.sort(
          (a, b) -> compareNumeric(menorSummary(a.getValue()), menorSummary(b.getValue())));
      Paths ordenadas = new Paths();
      ordenadas.setExtensions(originais.getExtensions());
      for (Map.Entry<String, PathItem> e : entradas) {
        ordenadas.addPathItem(e.getKey(), e.getValue());
      }
      openApi.setPaths(ordenadas);
    };
  }

  private static String menorSummary(PathItem item) {
    String menor = null;
    for (var op : item.readOperations()) {
      String s = op.getSummary();
      if (s == null) {
        continue;
      }
      if (menor == null || compareNumeric(s, menor) < 0) {
        menor = s;
      }
    }
    return menor == null ? "\uffff" : menor;
  }

  /**
   * Comparação que trata sequências numéricas como números — assim {@code 02.10.01} vem depois de
   * {@code 02.9.01}, e não entre {@code 02.1.05} e {@code 02.2.01}.
   */
  static int compareNumeric(String a, String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      char ca = a.charAt(i);
      char cb = b.charAt(j);
      if (Character.isDigit(ca) && Character.isDigit(cb)) {
        int endA = i;
        while (endA < a.length() && Character.isDigit(a.charAt(endA))) {
          endA++;
        }
        int endB = j;
        while (endB < b.length() && Character.isDigit(b.charAt(endB))) {
          endB++;
        }
        long na = Long.parseLong(a.substring(i, endA));
        long nb = Long.parseLong(b.substring(j, endB));
        if (na != nb) {
          return Long.compare(na, nb);
        }
        i = endA;
        j = endB;
      } else {
        if (ca != cb) {
          return Character.compare(ca, cb);
        }
        i++;
        j++;
      }
    }
    return Integer.compare(a.length() - i, b.length() - j);
  }

  /**
   * Remove o campo {@code operationsSorter} do JSON servido em {@code /v3/api-docs/swagger-config}.
   * Esse JSON é buscado pelo Swagger UI via {@code configUrl} e sobrescreve a configuração inline
   * do {@code swagger-initializer.js}; sem essa remoção, a string-sentinela inválida acabava
   * substituindo a função JS real injetada inline e o Swagger UI caía no fallback (sem ordenação
   * correta).
   */
  @Bean
  public FilterRegistrationBean<OncePerRequestFilter> stripOperationsSorterFromSwaggerConfig() {
    OncePerRequestFilter filter =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest request, HttpServletResponse response, FilterChain chain)
              throws ServletException, IOException {
            ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
            chain.doFilter(request, wrapped);
            byte[] body = wrapped.getContentAsByteArray();
            if (body.length == 0) {
              wrapped.copyBodyToResponse();
              return;
            }
            String json = new String(body, StandardCharsets.UTF_8);
            String filtered =
                json.replaceAll(",\"operationsSorter\":\"[^\"]*\"", "")
                    .replaceAll("\"operationsSorter\":\"[^\"]*\",", "")
                    .replaceAll("\"operationsSorter\":\"[^\"]*\"", "");
            byte[] out = filtered.getBytes(StandardCharsets.UTF_8);
            wrapped.resetBuffer();
            wrapped.setContentLength(out.length);
            wrapped.getOutputStream().write(out);
            wrapped.copyBodyToResponse();
          }
        };
    FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(filter);
    reg.addUrlPatterns("/v3/api-docs/swagger-config");
    reg.setName("stripOperationsSorterFromSwaggerConfig");
    return reg;
  }

  /**
   * Substitui o sentinel string {@code "__OFICINA_OPERATIONS_SORTER__"} no {@code
   * swagger-initializer.js} pela função JS real, contornando a limitação do Springdoc de aceitar
   * apenas strings em {@code operationsSorter}.
   */
  @Bean
  public SwaggerIndexPageTransformer swaggerIndexPageTransformer(
      SwaggerUiConfigProperties swaggerUiConfig,
      SwaggerUiOAuthProperties swaggerUiOAuthProperties,
      SwaggerUiConfigParameters swaggerUiConfigParameters,
      SwaggerWelcomeCommon swaggerWelcomeCommon,
      ObjectMapperProvider objectMapperProvider) {
    return new SwaggerIndexPageTransformer(
        swaggerUiConfig,
        swaggerUiOAuthProperties,
        swaggerUiConfigParameters,
        swaggerWelcomeCommon,
        objectMapperProvider) {
      @Override
      public Resource transform(
          HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
          throws IOException {
        Resource transformed = super.transform(request, resource, chain);
        String filename = transformed.getFilename();
        if (filename == null || !filename.endsWith("swagger-initializer.js")) {
          return transformed;
        }
        try (InputStream in = transformed.getInputStream()) {
          String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
          String aspas = "\"" + SORTER_SENTINEL + "\"";
          if (!body.contains(aspas)) {
            return transformed;
          }
          body = body.replace(aspas, SORTER_FUNCTION);
          long lastModified = System.currentTimeMillis();
          return new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
              return filename;
            }

            @Override
            public long lastModified() {
              return lastModified;
            }
          };
        }
      }
    };
  }
}
