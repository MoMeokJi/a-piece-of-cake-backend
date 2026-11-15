package com.momeokji.aiDiarybackend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.momeokji.aiDiarybackend.dto.request.MusicCreateRequest;
import com.momeokji.aiDiarybackend.dto.request.MusicUpdateRequest;
import com.momeokji.aiDiarybackend.dto.response.MusicResponse;
import com.momeokji.aiDiarybackend.entity.Music;
import com.momeokji.aiDiarybackend.repository.MusicRepository;

/** 관리자 음악 등록·수정의 재생 시간 처리. */
class MusicServiceTest {

	MusicRepository musicRepository = mock(MusicRepository.class);
	OpenAiService openAiService = mock(OpenAiService.class);
	MusicService service = new MusicService(musicRepository, openAiService);

	@Test
	void createStoresDurationSeconds() {
		when(musicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		MusicResponse out = service.createMusic(new MusicCreateRequest("vid", "제목", "가수", "calm", 182));

		assertEquals(182, out.getDurationSeconds());
	}

	@Test
	void createWithoutDurationLeavesItNull() {
		when(musicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		MusicResponse out = service.createMusic(new MusicCreateRequest("vid", "제목", "가수", "calm", null));

		assertNull(out.getDurationSeconds());
	}

	@Test
	void updateChangesDurationOnlyWhenGiven() {
		Music music = Music.builder().id(1L).videoId("vid").title("제목").artist("가수").mood("calm").durationSeconds(182).build();
		when(musicRepository.findById(1L)).thenReturn(Optional.of(music));

		service.updateMusic(1L, new MusicUpdateRequest(null, "새 제목", null, null, null));
		assertEquals(182, music.getDurationSeconds());

		service.updateMusic(1L, new MusicUpdateRequest(null, null, null, null, 200));
		assertEquals(200, music.getDurationSeconds());
	}
}
