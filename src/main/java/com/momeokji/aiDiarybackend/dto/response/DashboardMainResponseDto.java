package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardMainResponseDto {

	private final long userCount;
	private final long diaryCount;
	private final long questionCount;
	private final long musicCount;
}