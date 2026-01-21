package com.momeokji.aiDiarybackend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class DiaryPatchResponseDto {
	private final Long diaryId;
}