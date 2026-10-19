package com.momeokji.aiDiarybackend.service.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.momeokji.aiDiarybackend.config.OAuthProperties;

class HttpAppleAuthClientTest {

	static final String BASE = "https://apple.test";

	MockRestServiceServer server;
	HttpAppleAuthClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		AppleClientSecretFactory secretFactory = mock(AppleClientSecretFactory.class);
		when(secretFactory.create()).thenReturn("client-secret-jwt");
		OAuthProperties props = new OAuthProperties(new OAuthProperties.Google(null),
			new OAuthProperties.Apple("com.momeokji.cake", "TEAM", "KID", null, null, BASE));
		client = new HttpAppleAuthClient(builder, props, secretFactory);
	}

	@Test
	void exchangeReturnsRefreshToken() {
		server.expect(requestTo(BASE + "/auth/token"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().formData(form("client_id", "com.momeokji.cake", "client_secret", "client-secret-jwt",
				"code", "auth-code-1", "grant_type", "authorization_code")))
			.andRespond(withSuccess("{\"access_token\":\"a\",\"refresh_token\":\"rt-1\",\"id_token\":\"x\"}", MediaType.APPLICATION_JSON));

		Optional<String> result = client.exchangeAuthorizationCode("auth-code-1");

		assertThat(result).contains("rt-1");
		server.verify();
	}

	@Test
	void exchangeFailureIsEmpty() {
		server.expect(requestTo(BASE + "/auth/token"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

		assertThat(client.exchangeAuthorizationCode("bad-code")).isEmpty();
	}

	@Test
	void exchangeWithoutRefreshTokenInBodyIsEmpty() {
		server.expect(requestTo(BASE + "/auth/token"))
			.andRespond(withSuccess("{\"access_token\":\"a\"}", MediaType.APPLICATION_JSON));

		assertThat(client.exchangeAuthorizationCode("code")).isEmpty();
	}

	@Test
	void exchangeWhenSecretCannotBeBuiltIsEmpty() {
		AppleClientSecretFactory broken = mock(AppleClientSecretFactory.class);
		when(broken.create()).thenThrow(new IllegalStateException("no key"));
		OAuthProperties props = new OAuthProperties(new OAuthProperties.Google(null),
			new OAuthProperties.Apple("com.momeokji.cake", "TEAM", "KID", null, null, BASE));
		HttpAppleAuthClient noKey = new HttpAppleAuthClient(RestClient.builder(), props, broken);

		assertThat(noKey.exchangeAuthorizationCode("code")).isEmpty();
	}

	@Test
	void revokeSendsRefreshTokenHint() {
		server.expect(requestTo(BASE + "/auth/revoke"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().formData(form("client_id", "com.momeokji.cake", "client_secret", "client-secret-jwt",
				"token", "rt-1", "token_type_hint", "refresh_token")))
			.andRespond(withSuccess());

		assertThat(client.revoke("rt-1")).isTrue();
		server.verify();
	}

	@Test
	void revokeFailureIsFalse() {
		server.expect(requestTo(BASE + "/auth/revoke")).andRespond(withServerError());

		assertThat(client.revoke("rt-1")).isFalse();
	}

	private static org.springframework.util.MultiValueMap<String, String> form(String... kv) {
		var map = new org.springframework.util.LinkedMultiValueMap<String, String>();
		for (int i = 0; i < kv.length; i += 2) {
			map.add(kv[i], kv[i + 1]);
		}
		return map;
	}
}
