package br.com.oficina.infrastructure.config;

import br.com.oficina.adapter.security.JwtAuthenticationFilter;
import br.com.oficina.adapter.security.JwtProperties;
import br.com.oficina.adapter.security.ServerlessJwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, ServerlessJwtProperties.class})
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtFilter;

  public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
    this.jwtFilter = jwtFilter;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /**
   * Hierarquia de roles: {@code FUNCIONARIO_DA_OFICINA} herda todas as permissões de {@code
   * TECNICO_DA_OFICINA}. Assim, um funcionário pode chamar tanto endpoints administrativos quanto
   * endpoints técnicos sem precisar de duas roles.
   */
  @Bean
  public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy("ROLE_FUNCIONARIO_DA_OFICINA > ROLE_TECNICO_DA_OFICINA");
  }

  @Bean
  public SecurityExpressionHandler<FilterInvocation> webExpressionHandler(
      RoleHierarchy roleHierarchy) {
    DefaultWebSecurityExpressionHandler handler = new DefaultWebSecurityExpressionHandler();
    handler.setRoleHierarchy(roleHierarchy);
    return handler;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a
                    // Infra / docs / login
                    .requestMatchers(
                        "/auth/login",
                        "/interno/clientes/autenticacao",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/consulta/**", "/ordens-servico/*/historico")
                    .hasRole("CLIENTE")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/ordens-servico/*/aprovar",
                        "/ordens-servico/*/rejeitar-refazer",
                        "/ordens-servico/*/rejeitar-cancelar",
                        "/ordens-servico/*/confirmar-pagamento")
                    .hasRole("CLIENTE")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
