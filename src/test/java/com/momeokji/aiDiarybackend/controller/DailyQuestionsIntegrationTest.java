package com.momeokji.aiDiarybackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.momeokji.aiDiarybackend.entity.Question;
import com.momeokji.aiDiarybackend.repository.QuestionRepository;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

/**
 * 문답 질문 조회 {@code GET /diaries/question}.
 * 앞 2개는 필수(건너뛰기 불가), 뒤 3개는 선택이고, 선택 질문을 건너뛸 때 바꿔 끼울 여분을 함께 준다.
 */
class DailyQuestionsIntegrationTest extends IntegrationTestSupport {

	@Autowired
	QuestionRepository questionRepository;

	@Test
	void givesUpToFiveOptionalSparesThatDoNotOverlapTheQuestions() throws Exception {
		seed("REQUIRED", 3);
		seed("OPTIONAL", 10);

		JsonNode body = fetch();
		List<String> questions = texts(body.get("questions"));
		List<String> spares = texts(body.get("optionalSpares"));

		assertThat(questions).hasSize(5);
		assertThat(questions.subList(0, 2)).allMatch(q -> q.startsWith("REQUIRED-"));
		assertThat(questions.subList(2, 5)).allMatch(q -> q.startsWith("OPTIONAL-"));

		assertThat(spares).hasSize(5);
		assertThat(spares).allMatch(q -> q.startsWith("OPTIONAL-"));
		assertThat(spares).doesNotHaveDuplicates();
		assertThat(spares).doesNotContainAnyElementsOf(questions);
	}

	@Test
	void givesOnlyTheOptionalQuestionsThatAreLeftWhenFewerThanFive() throws Exception {
		seed("REQUIRED", 2);
		seed("OPTIONAL", 5);

		JsonNode body = fetch();
		List<String> questions = texts(body.get("questions"));
		List<String> spares = texts(body.get("optionalSpares"));

		assertThat(questions).hasSize(5);
		assertThat(spares).hasSize(2);
		assertThat(spares).doesNotContainAnyElementsOf(questions);
	}

	@Test
	void sparesIsAnEmptyArrayWhenNoOptionalQuestionIsLeft() throws Exception {
		seed("REQUIRED", 2);
		seed("OPTIONAL", 3);

		JsonNode body = fetch();

		assertThat(texts(body.get("questions"))).hasSize(5);
		assertThat(body.get("optionalSpares").isArray()).isTrue();
		assertThat(body.get("optionalSpares")).isEmpty();
	}

	@Test
	void requiredQuestionsNeverAppearAsSpares() throws Exception {
		seed("REQUIRED", 8);
		seed("OPTIONAL", 4);

		List<String> spares = texts(fetch().get("optionalSpares"));

		assertThat(spares).hasSize(1);
		assertThat(spares).allMatch(q -> q.startsWith("OPTIONAL-"));
	}

	/** 같은 문구가 두 행에 들어 있어도 한 화면에 같은 질문이 두 번 나오면 안 된다. */
	@Test
	void sameTextStoredTwiceIsShownOnlyOnce() throws Exception {
		seed("REQUIRED", 2);
		for (String content : List.of("같은 질문", "같은 질문", "질문 B", "질문 C", "질문 D")) {
			questionRepository.save(Question.builder().category("OPTIONAL").content(content).build());
		}

		JsonNode body = fetch();
		List<String> shown = new ArrayList<>(texts(body.get("questions")));
		shown.addAll(texts(body.get("optionalSpares")));

		assertThat(shown).doesNotHaveDuplicates();
		assertThat(shown).hasSize(6);
	}

	/** content 컬럼은 NULL 을 허용한다. DB 에 직접 들어간 빈 행 때문에 조회가 깨지면 안 된다. */
	@Test
	void rowsWithoutTextAreIgnored() throws Exception {
		seed("REQUIRED", 2);
		seed("OPTIONAL", 4);
		questionRepository.save(Question.builder().category("OPTIONAL").content(null).build());
		questionRepository.save(Question.builder().category("OPTIONAL").content("   ").build());

		JsonNode body = fetch();
		List<String> shown = new ArrayList<>(texts(body.get("questions")));
		shown.addAll(texts(body.get("optionalSpares")));

		assertThat(shown).hasSize(6);
		assertThat(shown).allMatch(q -> q.startsWith("REQUIRED-") || q.startsWith("OPTIONAL-"));
	}

	private void seed(String category, int count) {
		for (int i = 1; i <= count; i++) {
			questionRepository.save(Question.builder().category(category).content(category + "-" + i).build());
		}
	}

	private JsonNode fetch() throws Exception {
		String token = loginAsMember("question-reader");
		String body = mockMvc.perform(get("/diaries/question").header(AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body);
	}

	private static List<String> texts(JsonNode array) {
		assertThat(array).as("배열 필드가 응답에 있어야 한다").isNotNull();
		List<String> out = new ArrayList<>();
		array.forEach(n -> out.add(n.asText()));
		return out;
	}
}
