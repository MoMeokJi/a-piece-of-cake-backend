package com.momeokji.aiDiarybackend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Getter
@Builder
@Jacksonized
public class DiaryGenerateRequestDto {

	@NotNull
	@Size(min = 1)
	@JsonProperty("target_set")
	private final List<Qna> targetSet;

	@Getter
	@Builder
	@Jacksonized
	public static class Qna {
		@NotBlank
		@JsonProperty("question")
		private final String question;


		@NotBlank
		@JsonProperty("answer")
		private final String answer;
	}
}
