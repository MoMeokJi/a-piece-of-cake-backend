package com.momeokji.aiDiarybackend.dto.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude
public class OpenAiRequestDto {
	private Prompt prompt;
	private final String input;

	@Getter
	@Builder
	public static class Prompt {		//prompt 설정
		private String id;
		private String version;
	}
}