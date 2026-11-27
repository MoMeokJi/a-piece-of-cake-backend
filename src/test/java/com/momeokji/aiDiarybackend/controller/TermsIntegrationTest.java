package com.momeokji.aiDiarybackend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.momeokji.aiDiarybackend.entity.Terms;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

/** 가입 화면 약관 목록(스펙 2026-11-27 4.2). 앱은 이 응답만으로 약관 시트를 그린다. */
class TermsIntegrationTest extends IntegrationTestSupport {

	@Test
	void listIsPublicAndOrderedAsShownOnTheSheet() throws Exception {
		mockMvc.perform(get("/auth/terms"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.terms.length()").value(4))
			.andExpect(jsonPath("$.terms[0].id").isNumber())
			.andExpect(jsonPath("$.terms[0].title").value("서비스 이용약관"))
			.andExpect(jsonPath("$.terms[0].required").value(true))
			.andExpect(jsonPath("$.terms[0].contentPath").value("/terms"))
			.andExpect(jsonPath("$.terms[1].title").value("개인정보 수집 및 이용"))
			.andExpect(jsonPath("$.terms[1].required").value(true))
			.andExpect(jsonPath("$.terms[1].contentPath").value("/privacy"))
			.andExpect(jsonPath("$.terms[2].title").value("AI 일기 생성을 위한 개인정보 국외 이전 동의"))
			.andExpect(jsonPath("$.terms[2].required").value(true))
			.andExpect(jsonPath("$.terms[2].contentPath").value("/ai-consent"))
			.andExpect(jsonPath("$.terms[3].title").value("기록 알림 받기"))
			.andExpect(jsonPath("$.terms[3].required").value(false))
			.andExpect(jsonPath("$.terms[3].contentPath").doesNotExist());
	}

	/** 앱이 약관 종류로 분기하지 않도록 code 와 버전은 내려주지 않는다. */
	@Test
	void listDoesNotExposeInternalFields() throws Exception {
		mockMvc.perform(get("/auth/terms"))
			.andExpect(jsonPath("$.terms[0].code").doesNotExist())
			.andExpect(jsonPath("$.terms[0].version").doesNotExist())
			.andExpect(jsonPath("$.terms[0].isActive").doesNotExist());
	}

	/** 개정하면 옛 버전을 끄고 새 버전 행을 만든다. 목록에는 켜진 행만 나온다. */
	@Test
	void supersededVersionIsReplacedByTheNewOne() throws Exception {
		Terms old = termsRepository.findByIsActiveTrueOrderByDisplayOrderAsc().get(2);
		old.deactivate();
		termsRepository.save(old);
		Terms revised = termsRepository.save(Terms.create("AI_OVERSEAS_TRANSFER", 2, "AI 국외 이전 동의(개정)", true, 3,
			"/ai-consent", LocalDateTime.now()));

		mockMvc.perform(get("/auth/terms"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.terms.length()").value(4))
			.andExpect(jsonPath("$.terms[2].id").value(revised.getId()))
			.andExpect(jsonPath("$.terms[2].title").value("AI 국외 이전 동의(개정)"));
	}

	/** 만료된 access 토큰이 붙어 있어도 공개 경로라 열려야 한다(로그아웃 직후 로그인 화면). */
	@Test
	void listIgnoresABrokenBearerToken() throws Exception {
		mockMvc.perform(get("/auth/terms").header("Authorization", "Bearer not-a-jwt"))
			.andExpect(status().isOk());
	}
}
