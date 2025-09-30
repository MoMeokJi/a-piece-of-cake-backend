package com.momeokji.aiDiarybackend.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DiaryGenerateRequestDto {
	@NotBlank
	private String userId;

	private List<Qna> targetSet;


	@Getter @Builder
	public static class Qna {
		@NotBlank
		private String question;

		@NotBlank
		private String answer;
	}
}