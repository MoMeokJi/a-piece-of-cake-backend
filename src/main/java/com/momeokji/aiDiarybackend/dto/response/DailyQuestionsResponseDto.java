package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Getter
@Builder
@Jacksonized
public class DailyQuestionsResponseDto {

	/** 항상 5개. 0~1번은 필수(건너뛰기 불가), 2~4번은 선택. */
	private final List<String> questions;

	/** 선택 질문을 건너뛸 때 바꿔 끼울 여분. 0~5개이고 {@link #questions} 와 겹치지 않는다. 없으면 빈 배열. */
	private final List<String> optionalSpares;

}
