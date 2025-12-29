package com.spa.booking.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest()
class GatewayApplicationTests {
	@DynamicPropertySource
	static void jwtProps(DynamicPropertyRegistry registry) {
		registry.add(
				"spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
				() -> "http://localhost:18081/realms/spa-booking/protocol/openid-connect/certs"
		);
	}

	@MockitoBean
	ReactiveJwtDecoder reactiveJwtDecoder;

	@Test
	void contextLoads() {
	}
}
