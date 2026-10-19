package com.momeokji.aiDiarybackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.momeokji.aiDiarybackend.entity.Admin;
import com.momeokji.aiDiarybackend.repository.AdminRepository;
import com.momeokji.aiDiarybackend.security.AccessTokenIssuer;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

class AdminAuthIntegrationTest extends IntegrationTestSupport {

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	AccessTokenIssuer issuer;

	@Test
	void loginReturnsTokenInBody() throws Exception {
		saveAdmin("staff", "pw-1", true, false);

		mockMvc.perform(login("staff", "pw-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.accessTokenExpiresIn").value(7200))
			.andExpect(jsonPath("$.isSuper").value(false))
			.andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION));
	}

	@Test
	void wrongIdAndWrongPasswordLookIdentical() throws Exception {
		saveAdmin("staff", "pw-1", true, false);

		JsonNode wrongId = problem(mockMvc.perform(login("nobody", "pw-1")).andExpect(status().isUnauthorized()).andReturn());
		JsonNode wrongPw = problem(mockMvc.perform(login("staff", "wrong")).andExpect(status().isUnauthorized()).andReturn());

		assertThat(wrongId.get("code").asText()).isEqualTo("ADMIN_LOGIN_FAILED");
		assertThat(wrongPw.get("code").asText()).isEqualTo("ADMIN_LOGIN_FAILED");
		assertThat(wrongId.get("detail").asText()).isEqualTo(wrongPw.get("detail").asText());
	}

	@Test
	void pendingAdminWithRightPasswordIsNotApproved() throws Exception {
		saveAdmin("pending", "pw-1", false, false);

		mockMvc.perform(login("pending", "pw-1"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ADMIN_NOT_APPROVED"));
	}

	@Test
	void pendingAdminWithWrongPasswordIsLoginFailed() throws Exception {
		saveAdmin("pending", "pw-1", false, false);

		mockMvc.perform(login("pending", "wrong"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("ADMIN_LOGIN_FAILED"));
	}

	@Test
	void deletedAdminCannotLogin() throws Exception {
		Admin admin = saveAdmin("gone", "pw-1", true, false);
		admin.delete(LocalDateTime.now());
		adminRepository.save(admin);

		mockMvc.perform(login("gone", "pw-1"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("ADMIN_LOGIN_FAILED"));
	}

	@Test
	void logoutInvalidatesExistingToken() throws Exception {
		saveAdmin("staff", "pw-1", true, false);
		String token = loginAndGetToken("staff", "pw-1");

		mockMvc.perform(get("/admin/dashboards/main").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());
		mockMvc.perform(post("/admin/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/admin/dashboards/main").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void deletingAdminInvalidatesTheirToken() throws Exception {
		saveAdmin("root", "pw-root", true, true);
		saveAdmin("target", "pw-1", true, false);
		String rootToken = loginAndGetToken("root", "pw-root");
		String targetToken = loginAndGetToken("target", "pw-1");

		mockMvc.perform(delete("/admin/admins")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + rootToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("adminId", "target"))))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/admin/dashboards/main").header(HttpHeaders.AUTHORIZATION, "Bearer " + targetToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void nonSuperCannotApprove() throws Exception {
		saveAdmin("staff", "pw-1", true, false);
		saveAdmin("pending", "pw-2", false, false);
		String token = loginAndGetToken("staff", "pw-1");

		mockMvc.perform(post("/admin/admins/approve")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("adminId", "pending"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void signupIsPublicAndCreatesPendingAdmin() throws Exception {
		mockMvc.perform(post("/admin/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("adminId", "newbie", "adminPwd", "pw-1"))))
			.andExpect(status().isNoContent());

		assertThat(adminRepository.findByAdminId("newbie")).get()
			.extracting(Admin::getIsValid).isEqualTo(false);
	}

	private Admin saveAdmin(String adminId, String rawPassword, boolean isValid, boolean isSuper) {
		return adminRepository.save(Admin.builder()
			.adminId(adminId).adminPwd(passwordEncoder.encode(rawPassword)).createdAt(LocalDateTime.now())
			.isValid(isValid).isSuper(isSuper).build());
	}

	private org.springframework.test.web.servlet.RequestBuilder login(String adminId, String adminPwd) throws Exception {
		return post("/admin/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("adminId", adminId, "adminPwd", adminPwd)));
	}

	private String loginAndGetToken(String adminId, String adminPwd) throws Exception {
		MvcResult result = mockMvc.perform(login(adminId, adminPwd)).andExpect(status().isOk()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
	}

	private JsonNode problem(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
