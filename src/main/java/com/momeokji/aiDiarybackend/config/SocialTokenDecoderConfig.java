package com.momeokji.aiDiarybackend.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/**
 * 소셜 ID 토큰 디코더(스펙 5.1~5.3). 공급자 JWKS 로 서명을 검증한다. Nimbus 가 키를 캐시하고,
 * 모르는 kid 가 오면 다시 받아온다.
 *
 * <p>검증 규칙은 정적 메서드로 두어 테스트 디코더가 같은 규칙을 쓴다.
 */
@Configuration
public class SocialTokenDecoderConfig {

	public static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
	public static final String APPLE_JWKS_URI = "https://appleid.apple.com/auth/keys";
	static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
	static final String APPLE_ISSUER = "https://appleid.apple.com";
	private static final Duration JWKS_TIMEOUT = Duration.ofSeconds(5);

	@Bean
	public JwtDecoder googleIdTokenDecoder(OAuthProperties props) {
		NimbusJwtDecoder decoder = jwksDecoder(GOOGLE_JWKS_URI);
		decoder.setJwtValidator(googleValidator(props.google().clientIds()));
		return decoder;
	}

	@Bean
	public JwtDecoder appleIdTokenDecoder(OAuthProperties props) {
		NimbusJwtDecoder decoder = jwksDecoder(APPLE_JWKS_URI);
		decoder.setJwtValidator(appleValidator(props.apple().clientId()));
		return decoder;
	}

	/** 만료 + iss ∈ {https://accounts.google.com, accounts.google.com} + aud 가 허용 목록과 겹침. */
	public static OAuth2TokenValidator<Jwt> googleValidator(List<String> clientIds) {
		List<String> allowed = clientIds == null ? List.of() : clientIds;
		return new DelegatingOAuth2TokenValidator<>(
			new JwtTimestampValidator(),
			new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && GOOGLE_ISSUERS.contains(String.valueOf(iss))),
			new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
				aud -> aud != null && aud.stream().anyMatch(allowed::contains)));
	}

	/** 만료 + iss = https://appleid.apple.com + aud 에 번들 ID 포함. */
	public static OAuth2TokenValidator<Jwt> appleValidator(String clientId) {
		return new DelegatingOAuth2TokenValidator<>(
			new JwtTimestampValidator(),
			new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && APPLE_ISSUER.equals(String.valueOf(iss))),
			new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
				aud -> aud != null && clientId != null && aud.contains(clientId)));
	}

	private static NimbusJwtDecoder jwksDecoder(String jwksUri) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(JWKS_TIMEOUT);
		factory.setReadTimeout(JWKS_TIMEOUT);
		return NimbusJwtDecoder.withJwkSetUri(jwksUri)
			.jwsAlgorithm(SignatureAlgorithm.RS256)
			.restOperations(new RestTemplate(factory))
			.build();
	}
}
