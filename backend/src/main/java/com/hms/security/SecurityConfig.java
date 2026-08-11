package com.hms.security;

import com.hms.infrastructure.tenant.TenantResolutionFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.hms.application.agent.AgentTokenService;
import com.hms.security.agent.AgentTokenAuthenticationFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final HmsUserDetailsService userDetailsService;
    private final TenantResolutionFilter tenantResolutionFilter;

    /**
     * Chain 1 — machine clients on {@code /agent/v1/**}.
     *
     * <p>STATELESS, and that is the whole point rather than a tidiness choice.
     * The human chain below sets {@code maximumSessions(1)} with
     * {@code maxSessionsPreventsLogin(true)}: one session per user, and a second
     * login is refused rather than evicting the first. An agent authenticating
     * into a session would therefore be capped at a single process forever, and
     * a session lost without a clean logout would lock it out entirely. Creating
     * no session at all sidesteps both.
     *
     * <p>Ordered ahead of the human chain and matched narrowly, so nothing about
     * the browser login flow changes. {@code TenantResolutionFilter} still runs
     * after authentication here, which is what gives agent requests the same
     * tenant and branch filtering as everyone else.
     *
     * <p>Note the path: the servlet context is {@code /api}, so this matches
     * {@code /api/agent/v1/**} externally. Token *management* lives on
     * {@code /agent/tokens} and is deliberately left to the human chain.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain agentFilterChain(
            HttpSecurity http,
            AgentTokenService agentTokenService,
            MeterRegistry meterRegistry) throws Exception {

        AgentTokenAuthenticationFilter agentAuth =
            new AgentTokenAuthenticationFilter(agentTokenService, meterRegistry);

        http
            .securityMatcher("/agent/v1/**")
            // No browser, no cookies, no CSRF token exchange.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(agentAuth, UsernamePasswordAuthenticationFilter.class)
            // Tenant resolution reads the principal this filter just established.
            .addFilterAfter(tenantResolutionFilter, AgentTokenAuthenticationFilter.class)
            .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write(
                    "{\"message\":\"Agent credentials required\","
                    + "\"data\":{\"code\":\"UNAUTHORIZED\",\"retryable\":false}}");
            }));

        return http.build();
    }

    /** Chain 2 — humans. Unchanged behaviour; only the ordering is new. */
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.maximumSessions(1).maxSessionsPreventsLogin(true).sessionRegistry(sessionRegistry()))
                .securityContext(ctx -> ctx.securityContextRepository(new HttpSessionSecurityContextRepository()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/logout", "/auth/forgot-password/**", "/actuator/health", "/hospitalProfile/logo").permitAll()
                        // WO-001/T-002: Prometheus scrape endpoint.
                        // SECURITY NOTE: this exposes operational metrics without
                        // authentication. It carries no patient data, but it does
                        // reveal traffic volumes and endpoint names. It MUST be
                        // restricted at the ingress/firewall layer to the monitoring
                        // network — do not publish it on the public origin.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // WO-008: NHCX posts payer responses here. It has no
                        // session with us, so this endpoint MUST be public —
                        // authentication comes entirely from the JWS signature
                        // inside the payload, which NhcxPayloadCodec verifies and
                        // throws on. Tenant is resolved from the correlation id we
                        // generated at submission time, not from the request.
                        // Restrict by source IP at the ingress to NHCX's published
                        // ranges; do not rely on the signature check alone for
                        // rate limiting.
                        .requestMatchers("/nhcx/callback/**").permitAll()
                        // ABDM Consent Manager callbacks. Same reasoning as NHCX
                        // above: the caller is a gateway, not a hospital user, so
                        // there is no session and no role that a permission check
                        // could evaluate. Without this exemption the consent
                        // notification 401s before reaching the controller, and a
                        // patient's approval never arrives — the request sits at
                        // PENDING_APPROVAL with nothing anywhere to explain it.
                        // Restrict by source IP at the ingress to ABDM's published
                        // ranges.
                        .requestMatchers("/abdm/callback/**").permitAll()
                        // Public tenant list for the login screen dropdown (active tenants only).
                        .requestMatchers("/tenants/public").permitAll()
                        .requestMatchers("/patients/eRegister", "/patients/eRegister/search").permitAll()
                        .requestMatchers("/session", "/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                // Resolve tenant AFTER authentication so the principal is available.
                .addFilterAfter(tenantResolutionFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter()
                                    .write("{\"success\":false,\"message\":\"Authentication required\",\"data\":null}");
                        }))
                .formLogin(form -> form
                        .usernameParameter("UserName")
                        .passwordParameter("Password")
                        .loginProcessingUrl("/session")
                        .permitAll()
                        .successHandler((request, response, authentication) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.getWriter()
                                    .write("{\"success\":true,\"message\":\"Login successful\",\"data\":null}");
                        })
                        .failureHandler((request, response, exception) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter()
                                    .write("{\"success\":false,\"message\":\"Invalid credentials\",\"data\":null}");
                        }))
                .authenticationProvider(authenticationProvider())
                .logout(l -> l
                        .logoutUrl("/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("VSSID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.getWriter()
                                    .write("{\"success\":true,\"message\":\"Logout successful\",\"data\":null}");
                        }));
        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var p = new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration c) throws Exception {
        return c.getAuthenticationManager();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.1.*:*",
            "http://136.185.1.251:*",
            "https://asthyasoft.com/",
            "https://www.asthyasoft.com/",
            "https://demo.asthyasoft.com/",
            "https://demo.asthyasoft.com"
        ));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }

    @Bean
    public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }
}
