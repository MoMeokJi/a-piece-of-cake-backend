package com.momeokji.aiDiarybackend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * OpenAI Responses API 설정. 프롬프트 본문(System 메시지)은 {@code classpath:prompts/<key>.md} 에 두고,
 * 여기에는 키별 모델·샘플링 설정만 둔다. 대시보드 저장 프롬프트(ID/버전)는 더 이상 쓰지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "openai")
public record OpenAiConfig(
	@NotBlank String apiUrl,
	@NotBlank String secretKey,
	String beta,
	@NotEmpty Map<String, @Valid Prompt> prompts
) {
	/**
	 * @param model           Responses API 의 {@code model}
	 * @param temperature     없으면 요청에 넣지 않는다(OpenAI 기본값)
	 * @param maxOutputTokens 없으면 요청에 넣지 않는다
	 */
	public record Prompt(
		@NotBlank String model,
		Double temperature,
		Integer maxOutputTokens
	) {}
}
