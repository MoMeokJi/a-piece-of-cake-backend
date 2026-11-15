package com.momeokji.aiDiarybackend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.momeokji.aiDiarybackend.config.OpenAiConfig;

/**
 * 프롬프트 System 메시지 전문을 기동 시 한 번 읽어 둔다. 설정({@code openai.prompts.<key>}) 에 있는
 * 키마다 {@code classpath:prompts/<key>.md} 가 있어야 하며, 없거나 비어 있으면 기동에 실패한다.
 */
@Component
public class OpenAiPromptLoader {

	private static final String LOCATION = "classpath:prompts/%s.md";

	private final Map<String, String> systemMessages;

	public OpenAiPromptLoader(OpenAiConfig config, ResourceLoader resourceLoader) {
		Map<String, String> loaded = new LinkedHashMap<>();
		for (String key : config.prompts().keySet()) {
			loaded.put(key, read(key, resourceLoader));
		}
		this.systemMessages = Map.copyOf(loaded);
	}

	/** {@code key} 프롬프트의 System 메시지 전문. 앞뒤 공백은 제거되어 있다. */
	public String system(String key) {
		String text = systemMessages.get(key);
		if (text == null) {
			throw new IllegalArgumentException("설정되지 않은 prompt key입니다: " + key);
		}
		return text;
	}

	private static String read(String key, ResourceLoader resourceLoader) {
		String location = LOCATION.formatted(key);
		Resource resource = resourceLoader.getResource(location);
		if (!resource.exists()) {
			throw new IllegalStateException("프롬프트 파일이 없습니다: " + location
				+ " (openai.prompts." + key + " 설정에 해당하는 System 메시지 파일을 두어야 합니다)");
		}
		String text;
		try {
			text = resource.getContentAsString(StandardCharsets.UTF_8).strip();
		} catch (IOException e) {
			throw new IllegalStateException("프롬프트 파일을 읽을 수 없습니다: " + location, e);
		}
		if (text.isEmpty()) {
			throw new IllegalStateException("프롬프트 파일이 비어 있습니다: " + location);
		}
		return text;
	}
}
