package gr.uoa.di.madgik.StatisticChartGenerator_App;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * When OIDC_ISSUER_URI env var is set:  /config/** requires JWT (writes need ADMIN role).
 * When not set (dev/test mode):         all endpoints are public.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LogManager.getLogger(SecurityConfig.class);

    @Value("${OIDC_ISSUER_URI:}")
    private String oidcIssuerUri;

    @Value("${statstool.oidc.roles-claim:roles}")
    private String rolesClaim;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            // Charts are embedded in iframes on third-party sites (monitor dashboards etc.):
            // drop Spring Security's default X-Frame-Options: DENY.
            .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        if (oidcIssuerUri != null && !oidcIssuerUri.isBlank()) {
            log.info("OIDC enabled — issuer: {}", oidcIssuerUri);
            http
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/health/**", "/prometheus", "/info").permitAll()
                    .requestMatchers(HttpMethod.GET,    "/config/**").authenticated()
                    .requestMatchers(HttpMethod.POST,   "/config/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT,    "/config/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/config/**").hasRole("ADMIN")
                    .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );
        } else {
            log.warn("OIDC_ISSUER_URI not set — /config endpoints are unprotected (dev/test mode)");
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(rolesClaim);
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * Explicit JwtDecoder using issuer-uri. Only created when OIDC is configured,
     * so Spring's auto-configuration never probes an undefined issuer endpoint.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        if (oidcIssuerUri != null && !oidcIssuerUri.isBlank()) {
            return NimbusJwtDecoder.withIssuerLocation(oidcIssuerUri).build();
        }
        // No-op decoder — never called because oauth2ResourceServer is not configured
        // when OIDC is disabled.
        return token -> { throw new UnsupportedOperationException("OIDC not configured"); };
    }
}
