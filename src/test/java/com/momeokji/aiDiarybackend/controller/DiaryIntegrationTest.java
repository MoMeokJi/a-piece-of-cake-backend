package com.momeokji.aiDiarybackend.controller;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.support.DiaryTestSupport;

/** 일기 API: 응답 형식(타임존, 타입, 색상, 음악), 정렬·페이지, 에러 코드. */
class DiaryIntegrationTest extends DiaryTestSupport {

	@Test
	void createdAtHasKstOffset() throws Exception {
		String token = loginAsMember("sub-1");
		Diary d = insertDiary(memberIdOf(token), LocalDateTime.of(2025, 9, 16, 21, 12));

		mockMvc.perform(get("/diaries/" + d.getDiaryId()).header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.createdAt").value("2025-09-16T21:12:00+09:00"));
	}

	@Test
	void missingDiaryIs404() throws Exception {
		String token = loginAsMember("sub-1");

		mockMvc.perform(get("/diaries/999").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"));
		mockMvc.perform(delete("/diaries/999").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"));
	}

	@Test
	void otherMembersDiaryIs404() throws Exception {
		String owner = loginAsMember("sub-1");
		Diary d = insertDiary(memberIdOf(owner), LocalDateTime.of(2025, 9, 16, 21, 12));
		String other = loginAsMember("sub-2");

		mockMvc.perform(get("/diaries/" + d.getDiaryId()).header(AUTHORIZATION, "Bearer " + other))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DIARY_NOT_FOUND"));
	}
}
