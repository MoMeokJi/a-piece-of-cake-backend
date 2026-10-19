package com.momeokji.aiDiarybackend.config;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * 앱/관리자 access 토큰({@link JwtConfig})과 소셜 ID 토큰({@link SocialTokenDecoderConfig}) 디코더가
 * 같은 모양으로 조립하는 검증기. 만료 + iss + aud 세 가지를 본다.
 */
public final class JwtValidators {

	private JwtValidators() {
	}

	/**
	 * 만료(시계 오차 60초) + {@code iss} 가 {@code issuers} 중 하나 + {@code aud} 가 {@code audience} 조건을 만족.
	 * {@code iss} 나 {@code aud} 가 없으면 실패한다.
	 */
	public static OAuth2TokenValidator<Jwt> timestampIssuerAudience(Set<String> issuers, Predicate<List<String>> audience) {
		return new DelegatingOAuth2TokenValidator<>(
			new JwtTimestampValidator(),
			new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && issuers.contains(String.valueOf(iss))),
			new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && audience.test(aud)));
	}
}
