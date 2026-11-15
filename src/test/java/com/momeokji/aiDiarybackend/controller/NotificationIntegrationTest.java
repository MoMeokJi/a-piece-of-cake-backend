package com.momeokji.aiDiarybackend.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

/** 알림 수신 토글({@code PUT /users/me/notification})과 {@code GET /users/me} 의 provider. */
class NotificationIntegrationTest extends IntegrationTestSupport {

	@Test
	void defaultIsOffAndProviderIsGoogle() throws Exception {
		String token = loginAsMember("sub-1");

		me(token)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notificationEnabled").value(false))
			.andExpect(jsonPath("$.provider").value("GOOGLE"));
	}

	@Test
	void toggleOnThenOff() throws Exception {
		String token = loginAsMember("sub-1");

		putNotification(token, "{\"enabled\":true}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notificationEnabled").value(true));
		me(token).andExpect(jsonPath("$.notificationEnabled").value(true));

		putNotification(token, "{\"enabled\":false}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notificationEnabled").value(false));
		me(token).andExpect(jsonPath("$.notificationEnabled").value(false));
	}

	@Test
	void enabledIsRequired() throws Exception {
		String token = loginAsMember("sub-1");

		putNotification(token, "{}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void notificationApiRequiresToken() throws Exception {
		mockMvc.perform(put("/users/me/notification").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
			.andExpect(status().isUnauthorized());
	}

	private ResultActions me(String token) throws Exception {
		return mockMvc.perform(get("/users/me").header(AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions putNotification(String token, String body) throws Exception {
		return mockMvc.perform(put("/users/me/notification")
			.header(AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}
}
