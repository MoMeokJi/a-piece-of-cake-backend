package com.momeokji.aiDiarybackend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "openai")
public record OpenAiConfig(
	@NotBlank String apiUrl,
	@NotBlank String secretKey,
	String beta,
	Map<String, Prompt> prompts
) {
	public record Prompt(
		@NotBlank String id,
		@NotBlank String version
	) {}
}