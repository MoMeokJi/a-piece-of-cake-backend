package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MusicUpdateRequest {

	private String videoId;
	private String title;
	private String artist;
	private String mood;

	/** 재생 시간(초). null 이면 유지. */
	@Positive(message = "durationSeconds는 1 이상이어야 합니다.")
	private Integer durationSeconds;
}