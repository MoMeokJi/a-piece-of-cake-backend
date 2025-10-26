package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Getter
@Builder
@Jacksonized
public class DailyQuestionsResponseDto {

	private final List<String> questions;

}
