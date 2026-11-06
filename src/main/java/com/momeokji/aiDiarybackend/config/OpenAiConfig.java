package com.momeokji.aiDiarybackend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Set;

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
	public OpenAiConfig {
		if (prompts != null) {
			prompts.forEach((key, prompt) -> prompt.validate(key));
		}
	}

	/**
	 * @param model           Responses API 의 {@code model}
	 * @param temperature     없으면 요청에 넣지 않는다(OpenAI 기본값). reasoning effort 가 {@code none} 이 아니면 쓸 수 없다.
	 * @param maxOutputTokens 없으면 요청에 넣지 않는다
	 * @param reasoningEffort reasoning 모델의 {@code reasoning.effort}. 없으면 요청에 넣지 않는다(모델 기본값, 보통 medium).
	 */
	public record Prompt(
		@NotBlank String model,
		Double temperature,
		Integer maxOutputTokens,
		String reasoningEffort
	) {
		private static final Set<String> EFFORTS = Set.of("none", "low", "medium", "high", "xhigh", "max");

		void validate(String key) {
			if (reasoningEffort == null) {
				return;
			}
			if (!EFFORTS.contains(reasoningEffort)) {
				throw new IllegalArgumentException("openai.prompts." + key + ".reasoning-effort=" + reasoningEffort
					+ " 는 지원하지 않습니다. 가능한 값: " + EFFORTS);
			}
			if (temperature != null && !"none".equals(reasoningEffort)) {
				throw new IllegalArgumentException("openai.prompts." + key + ": reasoning-effort 가 none 이 아니면 "
					+ "temperature 를 둘 수 없습니다(OpenAI 가 400 으로 거부). temperature 를 지우거나 effort 를 none 으로 두세요.");
			}
		}
	}
}
