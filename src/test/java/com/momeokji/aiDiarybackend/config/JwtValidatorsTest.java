package com.momeokji.aiDiarybackend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

/** 앱/관리자 access 토큰과 소셜 ID 토큰 디코더가 함께 쓰는 검증기 조립 규칙. */
class JwtValidatorsTest {

	final OAuth2TokenValidator<Jwt> validator = JwtValidators.timestampIssuerAudience(
		Set.of("https://issuer.a", "issuer.a"), aud -> aud.contains("app"));

	@Test
	void acceptsAnyListedIssuerWithMatchingAudience() {
		assertThat(validator.validate(jwt("https://issuer.a", List.of("app"), 600)).hasErrors()).isFalse();
		assertThat(validator.validate(jwt("issuer.a", List.of("other", "app"), 600)).hasErrors()).isFalse();
	}

	@Test
	void rejectsUnknownIssuer() {
		assertThat(validator.validate(jwt("https://issuer.b", List.of("app"), 600)).hasErrors()).isTrue();
	}

	@Test
	void rejectsMissingIssuer() {
		assertThat(validator.validate(jwt(null, List.of("app"), 600)).hasErrors()).isTrue();
	}

	@Test
	void rejectsAudienceNotPassingPredicate() {
		assertThat(validator.validate(jwt("https://issuer.a", List.of("admin"), 600)).hasErrors()).isTrue();
	}

	@Test
	void rejectsMissingAudience() {
		assertThat(validator.validate(jwt("https://issuer.a", null, 600)).hasErrors()).isTrue();
	}

	@Test
	void rejectsExpiredToken() {
		assertThat(validator.validate(jwt("https://issuer.a", List.of("app"), -600)).hasErrors()).isTrue();
	}

	private static Jwt jwt(String issuer, List<String> aud, long expiresInSeconds) {
		Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);
		Jwt.Builder b = Jwt.withTokenValue("t")
			.header("alg", "none")
			.subject("s")
			.issuedAt(expiresAt.minusSeconds(60))
			.expiresAt(expiresAt);
		if (issuer != null) {
			b.claim(JwtClaimNames.ISS, issuer);
		}
		if (aud != null) {
			b.claim(JwtClaimNames.AUD, aud);
		}
		return b.build();
	}
}
