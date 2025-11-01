package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class DiaryConfirmResponseDto {
	private final Long diaryId;
	private final String content;
	private final LocalDateTime createdAt;
	private final String summary;
	private final List<String> images;
	private final List<String> recommandColors;
	//음악 추가해야 함
}