package com.momeokji.aiDiarybackend.dto.response;

import com.momeokji.aiDiarybackend.entity.DiaryType;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class DiaryConfirmResponseDto {
	private final Long diaryId;
	private final DiaryType type;
	private final String content;
	private final OffsetDateTime createdAt; // KST, +09:00
	private final String summary;
	private final List<String> images;
	private final List<ColorDto> colors;
	private final MusicDto music;

	@Getter @Builder
	public static class MusicDto {
		private final String title;
		private final String artist;
		private final String videoId;
	}
}