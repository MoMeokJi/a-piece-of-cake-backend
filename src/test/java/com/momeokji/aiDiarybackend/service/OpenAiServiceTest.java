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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

	/**
	 * reasoning 을 켠 모델은 {@code output} 맨 앞에 본문이 없는 reasoning 항목을 두고 그 뒤에 message 를 준다.
	 * 첫 항목만 보면 본문을 놓친다. 2026-11-15 실호출에서 초안 생성(effort=low)이 이 때문에 500 이 났다.
	 */
	@ParameterizedTest
	@ValueSource(strings = {
		"{\"type\":\"reasoning\",\"summary\":[]}",
		"{\"type\":\"reasoning\",\"summary\":[],\"content\":[]}"
	})
	void returnsMessageTextWhenReasoningItemComesFirst(String reasoningItem) {
		String response = "{\"status\":\"completed\",\"output\":[" + reasoningItem
			+ ",{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\" 생성된 일기 \"}]}]}";
		OpenAiService service = service(config(null), capturing(new AtomicReference<>(), response));

		assertEquals("생성된 일기", service.call("colors", Map.of("text", "x")));
	}

	/** reasoning 항목이 사고 과정 텍스트를 담아 오더라도 일기 본문에 섞이면 안 된다. */
	@Test
	void reasoningTextIsNotMixedIntoTheResult() {
		String response = "{\"output\":[{\"type\":\"reasoning\",\"content\":[{\"type\":\"reasoning_text\",\"text\":\"생각 중\"}]},"
			+ "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"생성된 일기\"}]}]}";
		OpenAiService service = service(config(null), capturing(new AtomicReference<>(), response));

		assertEquals("생성된 일기", service.call("colors", Map.of("text", "x")));
	}

	/** SDK 의 {@code output_text} 와 같게, message 의 output_text 조각을 순서대로 이어 붙인다. */
	@Test
	void joinsAllOutputTextPartsOfMessages() {
		String response = "{\"output\":[{\"type\":\"message\",\"content\":["
			+ "{\"type\":\"output_text\",\"text\":\"앞\"},{\"type\":\"output_text\",\"text\":\"뒤\"}]}]}";
		OpenAiService service = service(config(null), capturing(new AtomicReference<>(), response));

		assertEquals("앞뒤", service.call("colors", Map.of("text", "x")));
	}

	/** 본문이 없을 때는 왜 없는지(토큰 한도로 잘림 등)를 로그에서 바로 알 수 있어야 한다. */
	@Test
	void failureNamesStatusReasonAndItemTypesWhenThereIsNoText() {
		String response = "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},"
			+ "\"output\":[{\"type\":\"reasoning\",\"summary\":[]}]}";
		OpenAiService service = service(config(null), capturing(new AtomicReference<>(), response));

		RuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
			() -> service.call("colors", Map.of("text", "x")));

		String message = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
		assertTrue(message.contains("incomplete"), message);
		assertTrue(message.contains("max_output_tokens"), message);
		assertTrue(message.contains("reasoning"), message);
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
