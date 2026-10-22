package com.momeokji.aiDiarybackend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;
import com.momeokji.aiDiarybackend.support.TestSocialTokens;

/** 내 정보 API: {@code GET /users/me}, {@code PUT /users/me/nickname}. */
class UserIntegrationTest extends IntegrationTestSupport {

	static final String DEVICE = "11111111-1111-1111-1111-111111111111";

	@Test
	void newMemberHasNoNickname() throws Exception {
		String token = login("sub-1");

		me(token)
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.memberId").isString())
			.andExpect(jsonPath("$.nickname").value((Object) null));
	}

	@Test
	void setNicknameThenReadItBack() throws Exception {
		String token = login("sub-1");

		putNickname(token, "달콤한조각")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nickname").value("달콤한조각"));

		me(token).andExpect(jsonPath("$.nickname").value("달콤한조각"));
	}

	@Test
	void nicknameCanBeChangedAgain() throws Exception {
		String token = login("sub-1");
		putNickname(token, "first1").andExpect(status().isOk());

		putNickname(token, "Second2").andExpect(status().isOk());

		me(token).andExpect(jsonPath("$.nickname").value("Second2"));
	}

	@Test
	void nicknameRuleIsKoreanAlnumTwoToTenChars() throws Exception {
		String token = login("sub-1");

		for (String invalid : List.of("a", "열한글자가넘어가는닉네임", "공백 있음", "ㅋㅋㅋ", "emoji😀", "under_score", "", "   ")) {
			putNickname(token, invalid)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		}
		for (String valid : List.of("ab", "열글자짜리닉네임임", "한글abc123", "1234567890")) {
			putNickname(token, valid).andExpect(status().isOk());
		}
	}

	@Test
	void nicknameApisRequireToken() throws Exception {
		mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/users/me/nickname").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"abc\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void oldMeEndpointIsGone() throws Exception {
		String token = login("sub-1");

		mockMvc.perform(get("/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNotFound());
	}

	// ---- helpers ----

	private String login(String sub) throws Exception {
		Map<String, Object> req = Map.of(
			"idToken", TestSocialTokens.google(sub, "ios-client", sub + "@gmail.com"),
			"deviceId", DEVICE,
			"os", "IOS");
		String body = mockMvc.perform(post("/auth/social/google").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(req)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("accessToken").asText();
	}

	private ResultActions me(String token) throws Exception {
		return mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions putNickname(String token, String nickname) throws Exception {
		return mockMvc.perform(put("/users/me/nickname")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("nickname", nickname))));
	}
}
