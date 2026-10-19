package com.momeokji.aiDiarybackend.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 앱/관리자 access 토큰 디코더(스펙 4.1, 4.4, 7.2). HS256, 체인마다 다른 키.
 */
@Configuration
public class JwtConfig {

	public static final String MEMBER_AUDIENCE = "app";
	public static final String ADMIN_AUDIENCE = "admin";
	private static final int MIN_SECRET_BYTES = 32;

	/** UTF-8 32바이트 미만이면 기동 시점에 실패시킨다. */
	public static SecretKey hmacKey(String secret) {
		byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT secret 은 32바이트 이상이어야 합니다. (현재 " + bytes.length + "바이트)");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	public JwtDecoder memberJwtDecoder(JwtProperties props) {
		return decoder(props.member().secret(), props.issuer(), MEMBER_AUDIENCE);
	}

	@Bean
	public JwtDecoder adminJwtDecoder(JwtProperties props) {
		return decoder(props.admin().secret(), props.issuer(), ADMIN_AUDIENCE);
	}

	private static JwtDecoder decoder(String secret, String issuer, String audience) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(hmacKey(secret))
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decoder.setJwtValidator(validator(issuer, audience));
		return decoder;
	}

	/** 만료(시계 오차 60초) + iss 일치 + aud 포함. */
	static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
		return new DelegatingOAuth2TokenValidator<>(
			new JwtTimestampValidator(),
			new JwtIssuerValidator(issuer),
			new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)));
	}
}
