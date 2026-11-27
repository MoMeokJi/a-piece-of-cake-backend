package com.momeokji.aiDiarybackend.dto.openai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

/**
 * Responses API 요청 본문. {@code input} 은 메시지 배열이며 system(저장소의 프롬프트 전문) 다음에
 * user(입력 객체의 JSON 문자열) 순으로 둔다. 정적인 내용이 앞에 와야 프롬프트 캐시가 유지된다.
 * {@code temperature}, {@code max_output_tokens}, {@code reasoning} 은 설정이 있을 때만 보낸다.
 *
 * <p>{@code store} 는 항상 {@code false} 로 보낸다. 생략하면 OpenAI 가 응답(입력 포함)을 30일 이상 보관한다.
 * 우리는 응답 ID 를 다시 쓰지 않으므로 보관할 이유가 없고, 개인정보처리방침 5.1 의 보유 기간이 이 값을 전제로 한다.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiRequestDto {
	private final String model;
	private final List<Message> input;
	private final Double temperature;
	@JsonProperty("max_output_tokens")
	private final Integer maxOutputTokens;
	private final Reasoning reasoning;
	@Builder.Default
	private final Boolean store = false;

	/** reasoning 모델의 사고량. {@code none} 이면 temperature 를 함께 쓸 수 있다. */
	public record Reasoning(String effort) {}

	public record Message(String role, String content) {
		public static Message system(String content) {
			return new Message("system", content);
		}

		public static Message user(String content) {
			return new Message("user", content);
		}
	}
}
