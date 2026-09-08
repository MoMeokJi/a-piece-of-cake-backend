package com.momeokji.aiDiarybackend.dto.response;

import com.momeokji.aiDiarybackend.entity.Music;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MusicResponse {

	private Long id;
	private String videoId;
	private String title;
	private String artist;
	private String mood;

	public static MusicResponse from(Music music) {
		return MusicResponse.builder()
			.id(music.getId())
			.videoId(music.getVideoId())
			.title(music.getTitle())
			.artist(music.getArtist())
			.mood(music.getMood())
			.build();
	}
}