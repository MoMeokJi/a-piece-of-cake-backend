package com.momeokji.aiDiarybackend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DiaryListResponseDto {
	private Long diaryId;
	private String summary;
	private OffsetDateTime createdAt; // KST, +09:00
	private List<String> colors;
	private MusicDto music;

	@Getter @Builder
	public static class MusicDto {
		private String title;
		private String artist;
	}
}