package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class QuestionDeleteRequestDto {

	@NotNull(message = "id는 필수입니다.")
	private Integer id;
}