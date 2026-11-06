package com.momeokji.aiDiarybackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.config.OpenAiConfig;

import reactor.core.publisher.Mono;

/**
 * Responses API 요청 본문. 저장 프롬프트({@code prompt.id/version}) 대신 저장소의 System 메시지와
 * 입력 JSON 문자열을 {@code input} 배열로 보낸다. 정적인 system 이 앞, 동적인 user 가 뒤(프롬프트 캐시).
 */
class OpenAiServiceTest {

	private static final ObjectMapper om = new ObjectMapper();

	/** 보낸 요청 본문을 잡아 두고 고정 응답을 돌려주는 가짜 HTTP 교환. */
	private static ExchangeFunction capturing(AtomicReference<String> sentBody, String responseJson) {
		return request -> {
			MockClientHttpRequest mock = new MockClientHttpRequest(request.method(), request.url());
			request.writeTo(mock, ExchangeStrategies.withDefaults()).block();
			sentBody.set(mock.getBodyAsString().block());
			return Mono.just(ClientResponse.create(HttpStatus.OK)
				.header(HttpHeaders.CONTENT_TYPE, "application/json")
				.body(responseJson)
				.build());
		};
	}

	private static OpenAiConfig config(Integer maxOutputTokens) {
		return configWith(new OpenAiConfig.Prompt("gpt-4o-mini", 0.8, maxOutputTokens, null));
	}

	private static OpenAiConfig configWith(OpenAiConfig.Prompt colors) {
		return new OpenAiConfig("http://openai.test/v1/responses", "sk-test", null, Map.of("colors", colors));
	}

	private static OpenAiService service(OpenAiConfig config, ExchangeFunction exchange) {
		return new OpenAiService(config, new OpenAiPromptLoader(config, new DefaultResourceLoader()),
			WebClient.builder().exchangeFunction(exchange));
	}

	private static final String OUTPUT_RESPONSE =
		"{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\" {\\\"colors\\\":[]} \"}]}]}";

	@Test
	void sendsSystemFileTextThenUserInputJsonWithoutPromptObject() throws Exception {
		AtomicReference<String> sent = new AtomicReference<>();
		OpenAiService service = service(config(2048), capturing(sent, OUTPUT_RESPONSE));
		Map<String, Object> inputObj = Map.of("text", "오늘은 비가 왔다");

		service.call("colors", inputObj);

		JsonNode body = om.readTree(sent.get());
		assertFalse(body.has("prompt"), body.toString());
		assertEquals("gpt-4o-mini", body.get("model").asText());
		assertEquals(0.8, body.get("temperature").asDouble());
		assertEquals(2048, body.get("max_output_tokens").asInt());

		JsonNode input = body.get("input");
		assertEquals(2, input.size());
		assertEquals("system", input.get(0).get("role").asText());
		String colorsFile = new ClassPathResource("prompts/colors.md").getContentAsString(StandardCharsets.UTF_8);
		assertEquals(colorsFile.strip(), input.get(0).get("content").asText());
		assertEquals("user", input.get(1).get("role").asText());
		assertEquals(om.writeValueAsString(inputObj), input.get(1).get("content").asText());
	}

	@Test
	void omitsMaxOutputTokensWhenNotConfigured() throws Exception {
		AtomicReference<String> sent = new AtomicReference<>();
		OpenAiService service = service(config(null), capturing(sent, OUTPUT_RESPONSE));

		service.call("colors", Map.of("text", "x"));

		JsonNode body = om.readTree(sent.get());
		assertFalse(body.has("max_output_tokens"), body.toString());
		assertFalse(body.has("reasoning"), body.toString());
		assertNotNull(body.get("temperature"));
	}

	/** reasoning 모델(gpt-6-luna 등)은 {@code reasoning.effort} 로 사고량을 정한다. 설정이 있을 때만 보낸다. */
	@Test
	void sendsReasoningEffortWhenConfigured() throws Exception {
		AtomicReference<String> sent = new AtomicReference<>();
		OpenAiService service = service(configWith(new OpenAiConfig.Prompt("gpt-6-luna", null, 2048, "low")),
			capturing(sent, OUTPUT_RESPONSE));

		service.call("colors", Map.of("text", "x"));

		JsonNode body = om.readTree(sent.get());
		assertEquals("gpt-6-luna", body.get("model").asText());
		assertEquals("low", body.get("reasoning").get("effort").asText());
		assertFalse(body.has("temperature"), body.toString());
	}

	@Test
	void returnsTrimmedTextFromOutputArray() {
		OpenAiService service = service(config(null), capturing(new AtomicReference<>(), OUTPUT_RESPONSE));

		assertEquals("{\"colors\":[]}", service.call("colors", Map.of("text", "x")));
	}

	@Test
	void returnsOutputTextWhenPresent() {
		OpenAiService service = service(config(null),
			capturing(new AtomicReference<>(), "{\"output_text\":\" 생성된 일기 \"}"));

		assertEquals("생성된 일기", service.call("colors", Map.of("text", "x")));
	}

	@Test
	void unknownPromptKeyFails() {
		OpenAiService service = service(config(null), capturing(new AtomicReference<>(), OUTPUT_RESPONSE));

		RuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
			() -> service.call("nope", Map.of()));
		assertTrue(e.getMessage() != null || e.getCause() != null);
		assertTrue(List.of("nope").stream().anyMatch(k -> String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()).contains(k)));
	}
}
