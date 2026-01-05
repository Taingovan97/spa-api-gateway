package com.spa.booking.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;

import com.spa.booking.gateway.utils.KeycloakRoleConverter;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtResourceServerProperties.class)
public class SecurityConfig {
    // Token iss thực tế của bạn
    private static final String EXPECTED_ISSUER = "http://localhost:18081/realms/spa-booking";

    /**
     * Build a decoder from JWKS URI (reachable inside K8s),
     * then validate issuer claim == EXPECTED_ISSUER.
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtResourceServerProperties props) {
        String jwkSetUri = props.getJwkSetUri();
        if (!StringUtils.hasText(jwkSetUri)) {
            throw new IllegalStateException("""
                Missing required property:
                spring.security.oauth2.resourceserver.jwt.jwk-set-uri

                Example (Windows + kind):
                spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://host.docker.internal:18081/realms/spa-booking/protocol/openid-connect/certs
                """);
        }

        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> issuerValidator = new JwtIssuerValidator(EXPECTED_ISSUER);
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampValidator, issuerValidator));
        return decoder;
    }

    @Bean
    public SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        // public
                        .pathMatchers(
                            "/actuator/health",
                            "/actuator/health/**",
                            "/services/**").permitAll()

                        // protected by role
                        .pathMatchers("/admin/**").hasRole("ADMIN")
                        .pathMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .pathMatchers("/user/**").hasAnyRole("CUSTOMER", "STAFF", "ADMIN")

                        // everything else requires login
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new ReactiveJwtAuthenticationConverterAdapter(new KeycloakRoleConverter()))))
                .build();
    }
}
