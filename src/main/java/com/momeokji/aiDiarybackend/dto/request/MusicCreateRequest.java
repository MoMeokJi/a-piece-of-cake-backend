package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MusicCreateRequest {

	@NotBlank(message = "videoId는 필수입니다.")
	private String videoId;

	@NotBlank(message = "제목은 필수입니다.")
	private String title;

	@NotBlank(message = "가수는 필수입니다.")
	private String artist;

	@NotBlank(message = "mood는 필수입니다.")
	private String mood;

	/** 재생 시간(초). 선택. */
	@Positive(message = "durationSeconds는 1 이상이어야 합니다.")
	private Integer durationSeconds;
}