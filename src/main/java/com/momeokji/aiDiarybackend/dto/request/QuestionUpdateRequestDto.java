package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class QuestionUpdateRequestDto {

	@NotNull(message = "id는 필수입니다.")
	private Integer id;

	@NotBlank(message = "content는 필수입니다.")
	private String content;

	@NotBlank(message = "category는 필수입니다.")
	private String category;
}