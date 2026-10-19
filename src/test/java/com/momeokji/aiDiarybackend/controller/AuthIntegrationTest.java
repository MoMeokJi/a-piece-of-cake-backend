package com.momeokji.aiDiarybackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.momeokji.aiDiarybackend.config.JwtProperties;
import com.momeokji.aiDiarybackend.entity.SocialAccount;
import com.momeokji.aiDiarybackend.entity.SocialProvider;
import com.momeokji.aiDiarybackend.repository.SocialAccountRepository;
import com.momeokji.aiDiarybackend.security.AccessTokenIssuer;
import com.momeokji.aiDiarybackend.security.RefreshTokens;
import com.momeokji.aiDiarybackend.service.auth.apple.AppleTokenCipher;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;
import com.momeokji.aiDiarybackend.support.TestSocialTokens;

class AuthIntegrationTest extends IntegrationTestSupport {

	static final String DEVICE_A = "11111111-1111-1111-1111-111111111111";
	static final String DEVICE_B = "22222222-2222-2222-2222-222222222222";
	static final String GOOGLE_AUD = "ios-client";
	static final String APPLE_AUD = "com.momeokji.cake";
	static final String RAW_NONCE = "raw-nonce";

	@Autowired
	SocialAccountRepository socialAccountRepository;

	@Autowired
	AppleTokenCipher appleTokenCipher;

	@Autowired
	JwtProperties jwtProperties;

	@Test
	void firstGoogleLoginCreatesMember() throws Exception {
		JsonNode body = json(googleLogin("sub-1", DEVICE_A, "fcm-1")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.accessTokenExpiresIn").value(1800))
			.andExpect(jsonPath("$.refreshToken").isString())
			.andExpect(jsonPath("$.refreshTokenExpiresIn").value(5_184_000))
			.andExpect(jsonPath("$.isNewMember").value(true)));

		String memberId = me(body.get("accessToken").asText());
		assertThat(memberId).isNotBlank();
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "sub-1"))
			.get().extracting(SocialAccount::getMemberId).isEqualTo(memberId);
	}

	@Test
	void secondGoogleLoginReusesMember() throws Exception {
		String first = me(json(googleLogin("sub-1", DEVICE_A, null)).get("accessToken").asText());

		JsonNode second = json(googleLogin("sub-1", DEVICE_B, null)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isNewMember").value(false)));

		assertThat(me(second.get("accessToken").asText())).isEqualTo(first);
	}

	@Test
	void googleAndAppleAreSeparateMembers() throws Exception {
		String google = me(json(googleLogin("same-sub", DEVICE_A, null)).get("accessToken").asText());
		String apple = me(json(appleLogin("same-sub", DEVICE_B, null, "code-1")).get("accessToken").asText());

		assertThat(google).isNotEqualTo(apple);
	}

	@Test
	void refreshReturnsNewPair() throws Exception {
		JsonNode login = json(googleLogin("sub-1", DEVICE_A, null));

		JsonNode refreshed = json(refresh(login.get("refreshToken").asText(), DEVICE_A)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isNewMember").doesNotExist()));

		assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(login.get("refreshToken").asText());
		assertThat(me(refreshed.get("accessToken").asText())).isEqualTo(me(login.get("accessToken").asText()));
	}

	@Test
	void refreshAfterLogoutIsInvalid() throws Exception {
		JsonNode login = json(googleLogin("sub-1", DEVICE_A, null));

		mockMvc.perform(post("/auth/logout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("deviceId", DEVICE_A))))
			.andExpect(status().isNoContent());

		refresh(login.get("refreshToken").asText(), DEVICE_A)
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
	}

	@Test
	void refreshWithExpiredAccessHeaderStillWorks() throws Exception {
		JsonNode login = json(googleLogin("sub-1", DEVICE_A, null));
		String expired = new AccessTokenIssuer(jwtProperties, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-31)))
			.issueForMember("whoever").value();

		mockMvc.perform(post("/auth/refresh")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + expired)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("refreshToken", login.get("refreshToken").asText(), "deviceId", DEVICE_A))))
			.andExpect(status().isOk());
	}

	@Test
	void reusedRefreshAfterRotationIsRejected() throws Exception {
		// 유예 60초 안이라 재시도로 인정된다. 유예 밖 시나리오는 DeviceSessionServiceTest 가 가변 시계로 검사한다.
		JsonNode login = json(googleLogin("sub-1", DEVICE_A, null));
		refresh(login.get("refreshToken").asText(), DEVICE_A).andExpect(status().isOk());

		refresh(login.get("refreshToken").asText(), DEVICE_A).andExpect(status().isOk());
		refresh("garbage", DEVICE_A).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
	}

	@Test
	void logoutReturns204EvenTwice() throws Exception {
		JsonNode login = json(googleLogin("sub-1", DEVICE_A, null));
		String access = login.get("accessToken").asText();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/auth/logout")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("deviceId", DEVICE_A))))
				.andExpect(status().isNoContent());
		}
	}

	@Test
	void fcmTokenUpdateOnOwnDeviceSucceeds() throws Exception {
		JsonNode login = json(googleLogin("sub-1", DEVICE_A, "fcm-old"));

		mockMvc.perform(put("/auth/devices/fcm-token")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("deviceId", DEVICE_A, "fcmToken", "fcm-new"))))
			.andExpect(status().isNoContent());
	}

	@Test
	void fcmTokenUpdateOnForeignDeviceIs404() throws Exception {
		json(googleLogin("sub-1", DEVICE_A, null));
		JsonNode other = json(googleLogin("sub-2", DEVICE_B, null));

		mockMvc.perform(put("/auth/devices/fcm-token")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + other.get("accessToken").asText())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("deviceId", DEVICE_A, "fcmToken", "fcm-x"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"));
	}

	@Test
	void invalidDeviceIdOrOsIsValidationFailed() throws Exception {
		mockMvc.perform(post("/auth/social/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"idToken", TestSocialTokens.google("sub-1", GOOGLE_AUD, null), "deviceId", "abc", "os", "IOS"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(post("/auth/social/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"idToken", TestSocialTokens.google("sub-1", GOOGLE_AUD, null), "deviceId", DEVICE_A, "os", "WEB"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void invalidGoogleTokenIsSocialTokenInvalid() throws Exception {
		mockMvc.perform(post("/auth/social/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"idToken", TestSocialTokens.google("sub-1", "other-aud", null), "deviceId", DEVICE_A, "os", "IOS"))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("SOCIAL_TOKEN_INVALID"));
	}

	@Test
	void appleLoginStoresEncryptedRefreshToken() throws Exception {
		fakeAppleAuthClient.exchangeResult = Optional.of("apple-rt");

		appleLogin("sub-a", DEVICE_A, "a@privaterelay.appleid.com", "code-1").andExpect(status().isOk());

		SocialAccount account = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, "sub-a").orElseThrow();
		assertThat(account.getAppleRefreshToken()).isNotNull().isNotEqualTo("apple-rt");
		assertThat(appleTokenCipher.decrypt(account.getAppleRefreshToken())).isEqualTo("apple-rt");
		assertThat(account.getEmail()).isEqualTo("a@privaterelay.appleid.com");
		assertThat(fakeAppleAuthClient.exchangedCodes).containsExactly("code-1");
	}

	@Test
	void appleCodeExchangeFailureStillLogsInAndRetriesNextTime() throws Exception {
		fakeAppleAuthClient.exchangeResult = Optional.empty();
		appleLogin("sub-a", DEVICE_A, null, "code-1").andExpect(status().isOk()).andExpect(jsonPath("$.isNewMember").value(true));
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, "sub-a").orElseThrow()
			.getAppleRefreshToken()).isNull();

		fakeAppleAuthClient.exchangeResult = Optional.of("apple-rt-2");
		appleLogin("sub-a", DEVICE_A, null, "code-2").andExpect(status().isOk()).andExpect(jsonPath("$.isNewMember").value(false));

		assertThat(fakeAppleAuthClient.exchangedCodes).containsExactly("code-1", "code-2");
		assertThat(appleTokenCipher.decrypt(socialAccountRepository
			.findByProviderAndProviderUserId(SocialProvider.APPLE, "sub-a").orElseThrow().getAppleRefreshToken()))
			.isEqualTo("apple-rt-2");
	}

	@Test
	void appleReloginDoesNotExchangeAgainWhenTokenStored() throws Exception {
		appleLogin("sub-a", DEVICE_A, null, "code-1").andExpect(status().isOk());
		appleLogin("sub-a", DEVICE_A, null, "code-2").andExpect(status().isOk());

		assertThat(fakeAppleAuthClient.exchangedCodes).containsExactly("code-1");
	}

	@Test
	void appleReloginWithoutEmailKeepsStoredEmail() throws Exception {
		appleLogin("sub-a", DEVICE_A, "first@x.com", "code-1").andExpect(status().isOk());

		appleLogin("sub-a", DEVICE_A, null, "code-2").andExpect(status().isOk());

		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, "sub-a").orElseThrow().getEmail())
			.isEqualTo("first@x.com");
	}

	@Test
	void appleNonceMismatchIs401() throws Exception {
		Map<String, Object> req = appleRequest("sub-a", DEVICE_A, null, "code-1");
		req.put("nonce", "wrong");

		mockMvc.perform(post("/auth/social/apple").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("SOCIAL_TOKEN_INVALID"));
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.APPLE, "sub-a")).isEmpty();
	}

	@Test
	void removedEndpointsAreGone() throws Exception {
		mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"deviceId\":\"fcm\",\"mobileOS\":\"IOS\",\"preference\":\"x\"}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"deviceId\":\"fcm\"}"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/auth/refresh").header("Refresh-Token", "x"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void newMemberGetsRefsetSeed() throws Exception {
		String memberId = me(json(googleLogin("sub-1", DEVICE_A, null)).get("accessToken").asText());

		verify(redisService).seedDefaultsIfEmpty(eq(memberId), anyList());
	}

	// ---- helpers ----

	private ResultActions googleLogin(String sub, String deviceId, String fcmToken) throws Exception {
		Map<String, Object> req = new HashMap<>();
		req.put("idToken", TestSocialTokens.google(sub, GOOGLE_AUD, sub + "@gmail.com"));
		req.put("deviceId", deviceId);
		req.put("os", "IOS");
		if (fcmToken != null) {
			req.put("fcmToken", fcmToken);
		}
		return mockMvc.perform(post("/auth/social/google").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)));
	}

	private Map<String, Object> appleRequest(String sub, String deviceId, String email, String code) {
		Map<String, Object> req = new HashMap<>();
		req.put("identityToken", TestSocialTokens.apple(sub, APPLE_AUD, RefreshTokens.sha256Hex(RAW_NONCE), email));
		req.put("authorizationCode", code);
		req.put("nonce", RAW_NONCE);
		req.put("deviceId", deviceId);
		req.put("os", "IOS");
		return req;
	}

	private ResultActions appleLogin(String sub, String deviceId, String email, String code) throws Exception {
		return mockMvc.perform(post("/auth/social/apple").contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(appleRequest(sub, deviceId, email, code))));
	}

	private ResultActions refresh(String refreshToken, String deviceId) throws Exception {
		return mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken, "deviceId", deviceId))));
	}

	private String me(String accessToken) throws Exception {
		return mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
	}

	private JsonNode json(ResultActions actions) throws Exception {
		MvcResult result = actions.andReturn();
		assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
