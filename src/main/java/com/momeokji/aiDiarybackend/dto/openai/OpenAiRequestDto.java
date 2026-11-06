package com.momeokji.aiDiarybackend.dto.openai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Getter;

/**
 * Responses API 요청 본문. {@code input} 은 메시지 배열이며 system(저장소의 프롬프트 전문) 다음에
 * user(입력 객체의 JSON 문자열) 순으로 둔다. 정적인 내용이 앞에 와야 프롬프트 캐시가 유지된다.
 * {@code temperature}, {@code max_output_tokens} 는 설정이 있을 때만 보낸다.
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

	public record Message(String role, String content) {
		public static Message system(String content) {
			return new Message("system", content);
		}

		public static Message user(String content) {
			return new Message("user", content);
		}
	}
}
