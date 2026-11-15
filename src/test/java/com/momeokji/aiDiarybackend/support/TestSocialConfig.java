package com.momeokji.aiDiarybackend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.momeokji.aiDiarybackend.config.OAuthProperties;

/**
 * 통합 테스트용 소셜 설정. 공급자 JWKS 디코더를 테스트 키 디코더로 바꾸고(빈 이름 동일, overriding 허용),
 * 애플 HTTP 클라이언트를 fake 로 대체한다.
 */
@TestConfiguration
public class TestSocialConfig {

	@Bean
	public JwtDecoder googleIdTokenDecoder(OAuthProperties props) {
		return TestSocialTokens.googleDecoder(props.google().clientIds());
	}

	@Bean
	public JwtDecoder appleIdTokenDecoder(OAuthProperties props) {
		return TestSocialTokens.appleDecoder(props.apple().clientId());
	}

	@Bean
	@Primary
	public FakeAppleAuthClient fakeAppleAuthClient() {
		return new FakeAppleAuthClient();
	}
}
