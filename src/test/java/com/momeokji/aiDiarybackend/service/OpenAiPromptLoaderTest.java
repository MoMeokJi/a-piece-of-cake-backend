package com.momeokji.aiDiarybackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

import com.momeokji.aiDiarybackend.config.OpenAiConfig;

/** 프롬프트 System 메시지 전문을 {@code classpath:prompts/<key>.md} 에서 기동 시 한 번 읽는다. */
class OpenAiPromptLoaderTest {

	private static OpenAiConfig configWithKeys(String... keys) {
		Map<String, OpenAiConfig.Prompt> prompts = new java.util.LinkedHashMap<>();
		for (String key : keys) {
			prompts.put(key, new OpenAiConfig.Prompt("gpt-4o-mini", 0.8, 2048, null));
		}
		return new OpenAiConfig("http://localhost", "sk-test", null, prompts);
	}

	private static String fileText(String key) throws IOException {
		return new ClassPathResource("prompts/" + key + ".md").getContentAsString(StandardCharsets.UTF_8);
	}

	@Test
	void systemTextIsTheFileContentForEveryConfiguredKey() throws IOException {
		OpenAiPromptLoader loader = new OpenAiPromptLoader(configWithKeys("content", "colors"), new DefaultResourceLoader());

		assertEquals(fileText("content").strip(), loader.system("content"));
		assertEquals(fileText("colors").strip(), loader.system("colors"));
	}

	@Test
	void unknownKeyThrows() {
		OpenAiPromptLoader loader = new OpenAiPromptLoader(configWithKeys("colors"), new DefaultResourceLoader());

		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> loader.system("content"));
		assertTrue(e.getMessage().contains("content"), e.getMessage());
	}

	@Test
	void missingFileFailsAtStartupNamingThePath() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> new OpenAiPromptLoader(configWithKeys("no-such-prompt"), new DefaultResourceLoader()));

		assertTrue(e.getMessage().contains("prompts/no-such-prompt.md"), e.getMessage());
	}

	/** {@code src/test/resources/prompts/blank.md} 는 일부러 비워 둔 파일이다. */
	@Test
	void blankFileFailsAtStartupNamingThePath() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
			() -> new OpenAiPromptLoader(configWithKeys("blank"), new DefaultResourceLoader()));

		assertTrue(e.getMessage().contains("prompts/blank.md"), e.getMessage());
	}
}
