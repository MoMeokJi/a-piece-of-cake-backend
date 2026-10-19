package com.momeokji.aiDiarybackend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 공급자 설정(스펙 10장).
 */
@ConfigurationProperties("oauth")
public record OAuthProperties(
	Google google,
	Apple apple
) {
	/** @param clientIds 허용할 aud. iOS OAuth 클라이언트 ID 와 Android 용 server(web) 클라이언트 ID */
	public record Google(List<String> clientIds) {
		public Google {
			clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
		}
	}

	/**
	 * @param clientId iOS 앱 번들 ID(ID 토큰의 aud)
	 * @param teamId Apple Developer Team ID
	 * @param keyId Sign in with Apple 키 ID
	 * @param privateKeyPath .p8 파일 경로
	 * @param tokenEncryptionKey 애플 refresh 토큰 암호화 키(base64, 32바이트)
	 * @param baseUrl 애플 인증 서버 주소. 테스트에서 mock 서버로 바꾼다
	 */
	public record Apple(String clientId, String teamId, String keyId, String privateKeyPath,
		String tokenEncryptionKey, String baseUrl) {
		public Apple {
			baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://appleid.apple.com" : baseUrl;
		}
	}
}
