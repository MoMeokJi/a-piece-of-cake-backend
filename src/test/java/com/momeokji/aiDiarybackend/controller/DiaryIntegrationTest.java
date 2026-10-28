package com.momeokji.aiDiarybackend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.ResultActions;

import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.DiaryType;
import com.momeokji.aiDiarybackend.entity.Music;
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

	@Test
	void freeDiaryHasTypeFree() throws Exception {
		stubOpenAi();
		String token = loginAsMember("sub-1");

		confirm("/diaries/free", token, "자유 일기")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("FREE"));
	}

	@Test
	void qnaDiaryHasTypeQna() throws Exception {
		stubOpenAi();
		String token = loginAsMember("sub-1");

		String body = confirm("/diaries", token, "문답 일기")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("QNA"))
			.andReturn().getResponse().getContentAsString();
		long diaryId = objectMapper.readTree(body).get("diaryId").asLong();

		mockMvc.perform(get("/diaries/" + diaryId).header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.type").value("QNA"));
	}

	@Test
	void listReturnsPageMeta() throws Exception {
		String token = loginAsMember("sub-1");
		String memberId = memberIdOf(token);
		for (int day = 1; day <= 3; day++) {
			insertDiary(memberId, LocalDateTime.of(2025, 9, day, 12, 0));
		}

		mockMvc.perform(get("/diaries").param("size", "2").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(2))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.totalCount").value(3));

		mockMvc.perform(get("/diaries").param("size", "2").param("page", "1").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void sortOldestAndCreatedAlias() throws Exception {
		String token = loginAsMember("sub-1");
		String memberId = memberIdOf(token);
		Diary first = insertDiary(memberId, LocalDateTime.of(2025, 9, 1, 12, 0));
		insertDiary(memberId, LocalDateTime.of(2025, 9, 2, 12, 0));
		Diary last = insertDiary(memberId, LocalDateTime.of(2025, 9, 3, 12, 0));

		mockMvc.perform(get("/diaries").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.items[0].diaryId").value(last.getDiaryId()));
		mockMvc.perform(get("/diaries").param("sort", "oldest").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.items[0].diaryId").value(first.getDiaryId()));
		mockMvc.perform(get("/diaries").param("sort", "created").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.items[0].diaryId").value(first.getDiaryId()));
	}

	@Test
	void calendarAcceptsSort() throws Exception {
		String token = loginAsMember("sub-1");
		String memberId = memberIdOf(token);
		LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
		Diary first = insertDiary(memberId, thisMonth.atTime(9, 0));
		Diary last = insertDiary(memberId, thisMonth.plusDays(1).atTime(9, 0));

		mockMvc.perform(get("/diaries/calendar").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].diaryId").value(last.getDiaryId()));
		mockMvc.perform(get("/diaries/calendar").param("sort", "oldest").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$[0].diaryId").value(first.getDiaryId()));
	}

	@Test
	void unknownSortIsBadRequest() throws Exception {
		String token = loginAsMember("sub-1");

		mockMvc.perform(get("/diaries").param("sort", "weird").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void legacyColorPromptGivesHexWithoutName() throws Exception {
		stubOpenAi();
		String token = loginAsMember("sub-1");

		confirm("/diaries/free", token, "자유 일기")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.colors[0].hex").value("#C3E9A6"))
			.andExpect(jsonPath("$.colors[0].name").value((Object) null));
	}

	@Test
	void colorNamesComeFromPromptAndShowInDetailAndList() throws Exception {
		stubOpenAi();
		when(openAiService.call(eq("colors"), any()))
			.thenReturn("{\"colors\":[{\"name\":\"새싹 연두\",\"hex\":\"#C3E9A6\"},{\"name\":\"하양\",\"hex\":\"#FFFFFF\"}]}");
		String token = loginAsMember("sub-1");

		String body = confirm("/diaries/free", token, "자유 일기")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.colors[0].name").value("새싹 연두"))
			.andExpect(jsonPath("$.colors[0].hex").value("#C3E9A6"))
			.andReturn().getResponse().getContentAsString();
		long diaryId = objectMapper.readTree(body).get("diaryId").asLong();

		mockMvc.perform(get("/diaries/" + diaryId).header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.colors[1].name").value("하양"))
			.andExpect(jsonPath("$.colors[1].hex").value("#FFFFFF"));
		mockMvc.perform(get("/diaries").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.items[0].colors[0].name").value("새싹 연두"));
	}

	@Test
	void detailAndListMusicHaveDurationSeconds() throws Exception {
		String token = loginAsMember("sub-1");
		Music music = musicRepository.save(Music.builder().videoId("vid").title("제목").artist("가수").mood("calm").durationSeconds(182).build());
		Diary d = insertDiary(memberIdOf(token), LocalDateTime.of(2025, 9, 16, 21, 12), DiaryType.FREE, music.getId());

		mockMvc.perform(get("/diaries/" + d.getDiaryId()).header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.music.durationSeconds").value(182));
		mockMvc.perform(get("/diaries").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(jsonPath("$.items[0].music.durationSeconds").value(182));
	}

	// ---- helpers ----

	/** 문답 확정({@code /diaries}) 또는 자유 일기({@code /diaries/free}) 멀티파트 요청. */
	protected ResultActions confirm(String path, String token, String text) throws Exception {
		return mockMvc.perform(multipart(path)
			.part(new MockPart("text", text.getBytes(StandardCharsets.UTF_8)))
			.header(AUTHORIZATION, "Bearer " + token));
	}
}
