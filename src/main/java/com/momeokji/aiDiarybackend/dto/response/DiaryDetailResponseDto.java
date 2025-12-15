package com.momeokji.aiDiarybackend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DiaryDetailResponseDto {
	private Long diaryId;
	private String content;
	private LocalDateTime createdAt;

	private List<String> images;   // 일단 null
	private List<String> colors;   // HEX 코드

	private MusicDto music;
	private String feedbackMsg;    // 엔티티에 없으면 null

	@Getter @Builder
	public static class MusicDto {
		private String title;
		private String artist;
		private String videoId;
	}
}