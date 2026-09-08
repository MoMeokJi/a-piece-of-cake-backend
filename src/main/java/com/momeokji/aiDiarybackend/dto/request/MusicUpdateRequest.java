package com.momeokji.aiDiarybackend.dto.request;

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
}