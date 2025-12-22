package com.spa.booking.gateway.utils;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final Set<String> IGNORED_REALM_ROLES = Set.of(
            "offline_access",
            "uma_authorization"
    );

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        Collection<GrantedAuthority> scopeAuthorities = Optional.ofNullable(scopes.convert(jwt)).orElse(List.of());

        // extract realm roles
        Collection<GrantedAuthority> realmsAuthorities = extractRealmRoles(jwt);
        List<GrantedAuthority> authorities = Stream.concat(scopeAuthorities.stream(), realmsAuthorities.stream()).toList();

        System.out.println("JWT sub=" + jwt.getSubject());
        System.out.println("realm_access.roles=" + jwt.getClaimAsMap("realm_access"));
        System.out.println("AUTHORITIES=" + authorities);

        return new JwtAuthenticationToken(jwt, authorities);
    }

    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return List.of();

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.getOrDefault("roles", List.of());

        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(r -> !IGNORED_REALM_ROLES.contains(r))
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
    }
}
