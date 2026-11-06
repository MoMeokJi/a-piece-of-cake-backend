package com.momeokji.aiDiarybackend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * reasoning effort 가 {@code none} 이 아니면 OpenAI 가 {@code temperature} 를 거부(400)한다.
 * 운영에서 첫 호출에 터지지 않도록 설정 바인딩 시점에 막는다.
 */
class OpenAiConfigTest {

	private static OpenAiConfig config(String key, OpenAiConfig.Prompt prompt) {
		return new OpenAiConfig("http://localhost", "sk", null, Map.of(key, prompt));
	}

	@Test
	void temperatureWithReasoningEffortFailsNamingTheKey() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
			() -> config("summary", new OpenAiConfig.Prompt("gpt-6-luna", 0.8, null, "low")));

		assertTrue(e.getMessage().contains("summary"), e.getMessage());
		assertTrue(e.getMessage().contains("temperature"), e.getMessage());
	}

	@Test
	void temperatureWithEffortNoneIsAllowed() {
		assertDoesNotThrow(() -> config("colors", new OpenAiConfig.Prompt("gpt-6-luna", 0.8, 2048, "none")));
	}

	@Test
	void temperatureWithoutEffortIsAllowed() {
		assertDoesNotThrow(() -> config("colors", new OpenAiConfig.Prompt("gpt-4o-mini", 0.8, 2048, null)));
	}

	@Test
	void unknownEffortFailsNamingTheKey() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
			() -> config("music", new OpenAiConfig.Prompt("gpt-6-luna", null, null, "minimal")));

		assertTrue(e.getMessage().contains("music"), e.getMessage());
	}
}
