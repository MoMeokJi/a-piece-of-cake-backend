package com.momeokji.aiDiarybackend.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

/** 앱 최소 버전: 공개 {@code GET /app/min-version}, 관리자 {@code PUT /admin/app/versions/{platform}}. */
class AppVersionIntegrationTest extends IntegrationTestSupport {

	@Test
	void emptyTableGivesNullsWithoutToken() throws Exception {
		mockMvc.perform(get("/app/min-version"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ios").value((Object) null))
			.andExpect(jsonPath("$.android").value((Object) null));
	}

	@Test
	void adminUpsertThenPublicRead() throws Exception {
		String admin = loginAsAdmin();

		upsert(admin, "IOS", Map.of("minVersion", "1.2.0", "latestVersion", "1.3.0", "storeUrl", "https://apps.apple.com/x"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.minVersion").value("1.2.0"))
			.andExpect(jsonPath("$.latestVersion").value("1.3.0"))
			.andExpect(jsonPath("$.storeUrl").value("https://apps.apple.com/x"));
		upsert(admin, "IOS", Map.of("minVersion", "1.2.1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.minVersion").value("1.2.1"))
			.andExpect(jsonPath("$.latestVersion").value((Object) null));

		mockMvc.perform(get("/app/min-version"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ios.minVersion").value("1.2.1"))
			.andExpect(jsonPath("$.ios.latestVersion").value((Object) null))
			.andExpect(jsonPath("$.android").value((Object) null));
	}

	@Test
	void invalidVersionOrPlatformIs400() throws Exception {
		String admin = loginAsAdmin();

		upsert(admin, "IOS", Map.of("minVersion", "1.2"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		upsert(admin, "WEB", Map.of("minVersion", "1.2.0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void memberTokenCannotUpsert() throws Exception {
		String member = loginAsMember("sub-1");

		upsert(member, "IOS", Map.of("minVersion", "1.2.0"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void minVersionIgnoresBearerToken() throws Exception {
		mockMvc.perform(get("/app/min-version").header(AUTHORIZATION, "Bearer garbage"))
			.andExpect(status().isOk());
	}

	private ResultActions upsert(String token, String platform, Map<String, Object> body) throws Exception {
		return mockMvc.perform(put("/admin/app/versions/" + platform)
			.header(AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(new HashMap<>(body))));
	}
}
