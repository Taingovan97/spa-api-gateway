package com.spa.booking.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class UserContextGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public int getOrder() {
        return -1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .cast(JwtAuthenticationToken.class)
                .map(auth -> {
                    Jwt jwt = auth.getToken();

                    String sub = jwt.getSubject();      // get user id
                    String email = jwt.getClaimAsString("email");

                    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
                    @SuppressWarnings("unchecked")
                    List<String> roles = realmAccess == null ? List.of() : (List<String>) realmAccess.getOrDefault("roles", List.of());
                    ServerHttpRequest req = exchange.getRequest().mutate().headers(h -> {
                                h.add("X-User-Id", sub);
                                h.add("X-User-Email", email == null ? "" : email);
                                h.add("X-User-Roles", String.join(",", roles));
                            })
                            .build();
                    return exchange.mutate().request(req).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }
}
