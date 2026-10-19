package com.momeokji.aiDiarybackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.MemberDeviceRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.repository.SocialAccountRepository;
import com.momeokji.aiDiarybackend.security.RefreshTokens;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;
import com.momeokji.aiDiarybackend.support.TestSocialTokens;

class WithdrawIntegrationTest extends IntegrationTestSupport {

	static final String DEVICE_A = "11111111-1111-1111-1111-111111111111";
	static final String DEVICE_B = "22222222-2222-2222-2222-222222222222";

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	SocialAccountRepository socialAccountRepository;

	@Autowired
	MemberDeviceRepository memberDeviceRepository;

	@Test
	void withdrawRemovesSocialAndDevicesAndSoftDeletesMember() throws Exception {
		JsonNode login = googleLogin("sub-1", DEVICE_A);
		googleLogin("sub-1", DEVICE_B);
		String access = login.get("accessToken").asText();
		String memberId = me(access);

		mockMvc.perform(delete("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isNoContent());

		assertThat(socialAccountRepository.findByMemberId(memberId)).isEmpty();
		assertThat(memberDeviceRepository.findAll()).isEmpty();
		assertThat(memberRepository.findById(memberId)).get().extracting(Member::getIsValid).isEqualTo(false);
		verify(redisService).deleteUserData(memberId);
		mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void reloginAfterWithdrawCreatesNewMember() throws Exception {
		JsonNode first = googleLogin("sub-1", DEVICE_A);
		String firstId = me(first.get("accessToken").asText());
		mockMvc.perform(delete("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + first.get("accessToken").asText()))
			.andExpect(status().isNoContent());

		JsonNode second = googleLogin("sub-1", DEVICE_A);

		assertThat(second.get("isNewMember").asBoolean()).isTrue();
		assertThat(me(second.get("accessToken").asText())).isNotEqualTo(firstId);
	}

	@Test
	void oldRefreshTokenIsInvalidAfterWithdraw() throws Exception {
		JsonNode login = googleLogin("sub-1", DEVICE_A);
		mockMvc.perform(delete("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText()))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("refreshToken", login.get("refreshToken").asText(), "deviceId", DEVICE_A))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("REFRESH_INVALID"));
	}

	@Test
	void appleWithdrawRevokesStoredToken() throws Exception {
		JsonNode login = appleLogin("sub-a", DEVICE_A, "code-1");

		mockMvc.perform(delete("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText()))
			.andExpect(status().isNoContent());

		assertThat(fakeAppleAuthClient.revoked).containsExactly("apple-rt");
	}

	@Test
	void appleRevokeFailureStillWithdraws() throws Exception {
		JsonNode login = appleLogin("sub-a", DEVICE_A, "code-1");
		String memberId = me(login.get("accessToken").asText());
		fakeAppleAuthClient.revokeResult = false;

		mockMvc.perform(delete("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText()))
			.andExpect(status().isNoContent());

		assertThat(fakeAppleAuthClient.revoked).containsExactly("apple-rt");
		assertThat(socialAccountRepository.findByMemberId(memberId)).isEmpty();
		assertThat(memberRepository.findById(memberId)).get().extracting(Member::getIsValid).isEqualTo(false);
	}

	@Test
	void appleWithdrawWithoutStoredTokenDoesNotCallRevoke() throws Exception {
		fakeAppleAuthClient.exchangeResult = java.util.Optional.empty();
		JsonNode login = appleLogin("sub-a", DEVICE_A, "code-1");

		mockMvc.perform(delete("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + login.get("accessToken").asText()))
			.andExpect(status().isNoContent());

		assertThat(fakeAppleAuthClient.revoked).isEmpty();
	}

	private JsonNode googleLogin(String sub, String deviceId) throws Exception {
		String body = mockMvc.perform(post("/auth/social/google").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"idToken", TestSocialTokens.google(sub, "ios-client", null), "deviceId", deviceId, "os", "IOS"))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body);
	}

	private JsonNode appleLogin(String sub, String deviceId, String code) throws Exception {
		String body = mockMvc.perform(post("/auth/social/apple").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"identityToken", TestSocialTokens.apple(sub, "com.momeokji.cake", RefreshTokens.sha256Hex("n"), null),
					"authorizationCode", code, "nonce", "n", "deviceId", deviceId, "os", "IOS"))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body);
	}

	private String me(String accessToken) throws Exception {
		return mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}
}
