package com.momeokji.aiDiarybackend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

/** 스웨거 문서에 앱팀이 요청한 설명(에러 코드 표, 닉네임 규칙, 정렬 값)과 새 스키마가 실린다. */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true", "swagger.auth.enabled=false",
	"diary.daily-limit=5"})
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
			.contains("recent", "oldest")
			.contains("5개") // diary.daily-limit 을 따른다
			.contains("METHOD_NOT_ALLOWED", "NOT_FOUND");

		assertThat(docs.at("/components/schemas/DiaryPageResponseDto").isMissingNode()).isFalse();
		assertThat(docs.at("/components/schemas/MinVersionResponseDto").isMissingNode()).isFalse();
		assertThat(docs.at("/components/schemas/DiaryStatsResponseDto").isMissingNode()).isFalse();

		assertThat(docs.at("/paths/~1users~1me~1nickname/put/description").asText())
			.contains("^[가-힣A-Za-z0-9]{2,10}$");
		// 앱은 다섯 문항을 모두 보내지만 서버는 문항 수를 강제하지 않는다는 점이 설명에 있어야 한다
		assertThat(docs.at("/paths/~1diaries~1qna/post/description").asText())
			.contains("1개 이상", "여분");
		assertThat(docs.at("/paths/~1diaries/post/description").asText())
			.contains("429", "QNA", "colors");
		assertThat(docs.at("/paths/~1diaries~1free/post/description").asText())
			.contains("429", "FREE");
	}

	/**
	 * 운영은 앞단에서 https 를 끝내고 앱에는 http 로 넘긴다. 서버 주소를 비워 두면 springdoc 이 http 주소를 적어
	 * https 로 연 스웨거의 요청을 브라우저가 막는다(TypeError: Failed to fetch). 상대 주소면 연 페이지의 주소를 그대로 쓴다.
	 */
	@Test
	void apiDocsUseRelativeServerUrlRegardlessOfRequestScheme() throws Exception {
		String body = mockMvc.perform(get("/v3/api-docs").header("Host", "piece-of-cake.click"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		JsonNode servers = objectMapper.readTree(body).path("servers");

		assertThat(servers.size()).isEqualTo(1);
		assertThat(servers.get(0).path("url").asText()).isEqualTo("/");
	}
}
