package com.momeokji.aiDiarybackend.dto.response;

import com.momeokji.aiDiarybackend.entity.Question;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuestionResponseDto {

	private Integer id;
	private String category;
	private String content;

	public static QuestionResponseDto from(Question question) {
		return QuestionResponseDto.builder()
			.id(question.getId())
			.category(question.getCategory())
			.content(question.getContent())
			.build();
	}
}