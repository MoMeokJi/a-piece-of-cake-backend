package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class QuestionCreateRequestDto {

	@NotBlank(message = "content는 필수입니다.")
	private String content;

	@NotBlank(message = "category는 필수입니다.")
	private String category;
}