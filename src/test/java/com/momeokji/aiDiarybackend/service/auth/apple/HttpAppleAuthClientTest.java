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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.momeokji.aiDiarybackend.config.OAuthProperties;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

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
		assertThat(clientWithoutKey().exchangeAuthorizationCode("code")).isEmpty();
	}

	@Test
	void exchangeWhenSecretCannotBeBuiltLogsError() {
		ListAppender<ILoggingEvent> logs = captureLogs();

		clientWithoutKey().exchangeAuthorizationCode("code");

		assertThat(logs.list).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
		assertThat(logs.list).extracting(ILoggingEvent::getLevel).doesNotContain(Level.WARN);
	}

	@Test
	void revokeWhenSecretCannotBeBuiltLogsError() {
		ListAppender<ILoggingEvent> logs = captureLogs();

		assertThat(clientWithoutKey().revoke("rt-1")).isFalse();

		assertThat(logs.list).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
	}

	@Test
	void exchangeHttpFailureStaysWarn() {
		ListAppender<ILoggingEvent> logs = captureLogs();
		server.expect(requestTo(BASE + "/auth/token"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

		client.exchangeAuthorizationCode("bad-code");

		assertThat(logs.list).extracting(ILoggingEvent::getLevel).contains(Level.WARN);
		assertThat(logs.list).extracting(ILoggingEvent::getLevel).doesNotContain(Level.ERROR);
	}

	/** 설정·키 파일 문제로 client_secret 을 만들 수 없는 클라이언트. */
	private static HttpAppleAuthClient clientWithoutKey() {
		AppleClientSecretFactory broken = mock(AppleClientSecretFactory.class);
		when(broken.create()).thenThrow(new IllegalStateException("no key"));
		OAuthProperties props = new OAuthProperties(new OAuthProperties.Google(null),
			new OAuthProperties.Apple("com.momeokji.cake", "TEAM", "KID", null, null, BASE));
		return new HttpAppleAuthClient(RestClient.builder(), props, broken);
	}

	ListAppender<ILoggingEvent> appender;

	private ListAppender<ILoggingEvent> captureLogs() {
		appender = new ListAppender<>();
		appender.start();
		((Logger) LoggerFactory.getLogger(HttpAppleAuthClient.class)).addAppender(appender);
		return appender;
	}

	@AfterEach
	void detachLogs() {
		if (appender != null) {
			((Logger) LoggerFactory.getLogger(HttpAppleAuthClient.class)).detachAppender(appender);
		}
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
