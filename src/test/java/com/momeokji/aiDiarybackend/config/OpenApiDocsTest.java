package com.momeokji.aiDiarybackend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

/** 스웨거 문서에 앱팀이 요청한 설명(에러 코드 표, 닉네임 규칙, 정렬 값)과 새 스키마가 실린다. */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true", "swagger.auth.enabled=false"})
class OpenApiDocsTest extends IntegrationTestSupport {

	@Test
	void apiDocsCarryErrorCodeTableAndNewSchemas() throws Exception {
		String body = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		JsonNode docs = objectMapper.readTree(body);

		String description = docs.at("/info/description").asText();
		assertThat(description)
			.contains("DIARY_DAILY_LIMIT_EXCEEDED", "DIARY_NOT_FOUND", "TOKEN_EXPIRED", "TOKEN_INVALID", "REFRESH_INVALID")
			.contains("+09:00")
			.contains("recent", "oldest");

		assertThat(docs.at("/components/schemas/DiaryPageResponseDto").isMissingNode()).isFalse();
		assertThat(docs.at("/components/schemas/MinVersionResponseDto").isMissingNode()).isFalse();
		assertThat(docs.at("/components/schemas/DiaryStatsResponseDto").isMissingNode()).isFalse();

		assertThat(docs.at("/paths/~1users~1me~1nickname/put/description").asText())
			.contains("^[가-힣A-Za-z0-9]{2,10}$");
		assertThat(docs.at("/paths/~1diaries~1qna/post/description").asText())
			.contains("빼고");
	}
}
