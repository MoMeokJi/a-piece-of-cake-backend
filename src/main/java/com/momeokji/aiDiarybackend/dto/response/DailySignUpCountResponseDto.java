package com.momeokji.aiDiarybackend.dto.response;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailySignUpCountResponseDto {

	private final LocalDate date;
	private final long dailySignUpCount;
}