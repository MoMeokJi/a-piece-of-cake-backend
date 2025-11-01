package com.momeokji.aiDiarybackend.config;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "openai")
public class OpenAiConfig {
	private final String apiUrl;
	private final String secretKey;
	private final String beta;
	private final Map<String, Prompt> prompts;

	@Getter
	@AllArgsConstructor
	public static class Prompt {
		private final String id;      // 프롬프트 아이디
		private final String version; // 프롬프트 버전
	}
}