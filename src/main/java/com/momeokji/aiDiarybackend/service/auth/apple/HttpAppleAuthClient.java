package com.momeokji.aiDiarybackend.service.auth.apple;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.momeokji.aiDiarybackend.config.OAuthProperties;

import lombok.extern.slf4j.Slf4j;

/** {@link AppleAuthClient} 의 HTTP 구현. 토큰 원문은 로그에 남기지 않는다. */
@Slf4j
@Component
public class HttpAppleAuthClient implements AppleAuthClient {

	private final RestClient restClient;
	private final OAuthProperties.Apple apple;
	private final AppleClientSecretFactory clientSecretFactory;

	public HttpAppleAuthClient(RestClient.Builder builder, OAuthProperties props, AppleClientSecretFactory clientSecretFactory) {
		this.restClient = builder.baseUrl(props.apple().baseUrl()).build();
		this.apple = props.apple();
		this.clientSecretFactory = clientSecretFactory;
	}

	@Override
	public Optional<String> exchangeAuthorizationCode(String authorizationCode) {
		try {
			MultiValueMap<String, String> form = baseForm();
			form.add("code", authorizationCode);
			form.add("grant_type", "authorization_code");

			Map<?, ?> body = restClient.post()
				.uri("/auth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(Map.class);

			Object refreshToken = body == null ? null : body.get("refresh_token");
			if (refreshToken instanceof String s && !s.isBlank()) {
				return Optional.of(s);
			}
			log.warn("애플 code 교환 응답에 refresh_token 이 없습니다.");
			return Optional.empty();
		} catch (IllegalStateException e) {
			// 설정·키 파일 문제는 운영자가 고쳐야 하는 장애라 ERROR 로 남긴다. 애플 쪽 거부(WARN)와 구분한다.
			log.error("애플 client_secret 을 만들 수 없어 code 교환을 건너뜁니다: {}", e.getMessage());
			return Optional.empty();
		} catch (Exception e) {
			log.warn("애플 authorization code 교환 실패: {}", e.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public boolean revoke(String appleRefreshToken) {
		try {
			MultiValueMap<String, String> form = baseForm();
			form.add("token", appleRefreshToken);
			form.add("token_type_hint", "refresh_token");

			restClient.post()
				.uri("/auth/revoke")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity();
			return true;
		} catch (Exception e) {
			log.error("애플 토큰 revoke 실패: {}", e.getMessage());
			return false;
		}
	}

	private MultiValueMap<String, String> baseForm() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", apple.clientId());
		form.add("client_secret", clientSecretFactory.create());
		return form;
	}
}
