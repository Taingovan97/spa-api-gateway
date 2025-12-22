package com.spa.booking.gateway.config;

import com.spa.booking.gateway.utils.KeycloakRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        // public
                        .pathMatchers("/actuator/health", "/services/**").permitAll()

                        // protected by role
                        .pathMatchers("/admin/**").hasRole("ADMIN")
                        .pathMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .pathMatchers("/user/**").hasAnyRole("CUSTOMER",  "STAFF", "ADMIN")

                        // everything else requires login
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(new KeycloakRoleConverter()))))
                .build();
    }
}
