package com.momeokji.aiDiarybackend.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DiaryListResponseDto {
	private Long diaryId;
	private String summary;
	private LocalDateTime createdAt;
	private List<String> colors;
	private MusicDto music;           //일단은 빈거

	@Getter @Builder
	public static class MusicDto {
		private String title;
		private String artist;
	}
}