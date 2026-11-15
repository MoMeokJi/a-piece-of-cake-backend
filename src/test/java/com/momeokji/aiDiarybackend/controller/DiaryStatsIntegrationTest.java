package com.momeokji.aiDiarybackend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.ResultActions;

import com.momeokji.aiDiarybackend.common.time.KstTime;
import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.support.DiaryTestSupport;

/**
 * {@code GET /users/me/stats} 와 하루 3개 작성 제한.
 *
 * <p>서버 시계를 실제보다 10일 뒤로 고정한다. "오늘" 이 테스트 중에 바뀌지 않고(자정 경계 flaky 방지),
 * 서비스가 시스템 시계 대신 주입된 {@code Clock} 을 쓰는지도 드러난다. 토큰은 고정 시각 기준으로
 * 발급되어 exp 가 미래이므로 검증을 통과한다.
 */
@Import(DiaryStatsIntegrationTest.FixedClockConfig.class)
class DiaryStatsIntegrationTest extends DiaryTestSupport {

	@TestConfiguration
	static class FixedClockConfig {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.now().plus(Duration.ofDays(10)), KstTime.ZONE);
		}
	}

	@Autowired
	Clock clock;

	LocalDate today;

	@org.junit.jupiter.api.BeforeEach
	void todayFromInjectedClock() {
		today = LocalDate.now(clock);
	}

	@Test
	void statsCountTotalMonthToday() throws Exception {
		String token = loginAsMember("sub-1");
		String memberId = memberIdOf(token);
		insertDiary(memberId, today.withDayOfMonth(1).minusDays(1).atTime(12, 0)); // 지난달 마지막 날
		insertDiary(memberId, today.atTime(12, 0));
		if (today.getDayOfMonth() > 1) {
			insertDiary(memberId, today.minusDays(1).atTime(12, 0));
		} else {
			insertDiary(memberId, today.atTime(13, 0));
		}

		stats(token)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalCount").value(3))
			.andExpect(jsonPath("$.monthCount").value(2))
			.andExpect(jsonPath("$.todayCount").value(today.getDayOfMonth() > 1 ? 1 : 2));
	}

	@Test
	void todayCountRespectsKstMidnight() throws Exception {
		String token = loginAsMember("sub-1");
		String memberId = memberIdOf(token);
		insertDiary(memberId, today.minusDays(1).atTime(23, 59, 59));
		insertDiary(memberId, today.atStartOfDay());

		stats(token)
			.andExpect(jsonPath("$.todayCount").value(1))
			.andExpect(jsonPath("$.streakDays").value(2));
	}

	@Test
	void streakCountsConsecutiveDays() throws Exception {
		String token = loginAsMember("sub-1");
		String memberId = memberIdOf(token);
		insertDiary(memberId, today.atTime(9, 0));
		insertDiary(memberId, today.minusDays(1).atTime(9, 0));
		insertDiary(memberId, today.minusDays(3).atTime(9, 0));

		stats(token).andExpect(jsonPath("$.streakDays").value(2));
	}

	@Test
	void streakStartsFromYesterdayWhenNoDiaryToday() throws Exception {
		String token = loginAsMember("sub-1");
		String memberId = memberIdOf(token);
		insertDiary(memberId, today.minusDays(1).atTime(9, 0));
		insertDiary(memberId, today.minusDays(2).atTime(9, 0));

		stats(token).andExpect(jsonPath("$.streakDays").value(2));

		String other = loginAsMember("sub-2");
		insertDiary(memberIdOf(other), today.minusDays(2).atTime(9, 0));

		stats(other).andExpect(jsonPath("$.streakDays").value(0));
	}

	@Test
	void statsIncludeDailyLimit() throws Exception {
		String token = loginAsMember("sub-1");

		stats(token)
			.andExpect(jsonPath("$.dailyLimit").value(3))
			.andExpect(jsonPath("$.totalCount").value(0))
			.andExpect(jsonPath("$.streakDays").value(0));
	}

	@Test
	void fourthDiaryOfTheDayIs429() throws Exception {
		stubOpenAi();
		String token = loginAsMember("sub-1");
		String memberId = memberIdOf(token);
		for (int i = 0; i < 3; i++) {
			insertDiary(memberId, today.atTime(10 + i, 0));
		}

		confirmFree(token)
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("DIARY_DAILY_LIMIT_EXCEEDED"));
		mockMvc.perform(post("/diaries/qna")
				.header(AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"target_set\":[{\"question\":\"오늘 어땠어?\",\"answer\":\"좋았어\"}]}"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("DIARY_DAILY_LIMIT_EXCEEDED"));

		verify(openAiService, never()).call(any(), any());
	}

	@Test
	void deletedDiaryDoesNotCountTowardLimit() throws Exception {
		stubOpenAi();
		String token = loginAsMember("sub-1");
		String memberId = memberIdOf(token);
		Diary first = insertDiary(memberId, today.atTime(10, 0));
		insertDiary(memberId, today.atTime(11, 0));
		insertDiary(memberId, today.atTime(12, 0));

		mockMvc.perform(delete("/diaries/" + first.getDiaryId()).header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNoContent());

		confirmFree(token).andExpect(status().isCreated());
		stats(token).andExpect(jsonPath("$.todayCount").value(3));
	}

	/** 캘린더의 "이번 달" 기본값도 주입된 Clock 을 따른다. 시스템 시계를 쓰면 10일 뒤 달의 일기가 보이지 않는다. */
	@Test
	void calendarDefaultMonthFollowsInjectedClock() throws Exception {
		String token = loginAsMember("sub-1");
		insertDiary(memberIdOf(token), today.atTime(9, 0));

		mockMvc.perform(get("/diaries/calendar").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1));
	}

	// ---- helpers ----

	private ResultActions stats(String token) throws Exception {
		return mockMvc.perform(get("/users/me/stats").header(AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions confirmFree(String token) throws Exception {
		return mockMvc.perform(multipart("/diaries/free")
			.part(new MockPart("text", "자유 일기".getBytes(StandardCharsets.UTF_8)))
			.header(AUTHORIZATION, "Bearer " + token));
	}
}
