package com.spa.booking.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.jwt")
public class JwtResourceServerProperties {

    /**
     * JWKS endpoint URL.
     * Example:
     * http://host.docker.internal:18081/realms/spa-booking/protocol/openid-connect/certs
     */
    private String jwkSetUri;

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }
}
