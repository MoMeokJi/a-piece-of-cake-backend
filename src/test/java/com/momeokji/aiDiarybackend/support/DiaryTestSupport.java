package com.momeokji.aiDiarybackend.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.DiaryType;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MusicRepository;
import com.momeokji.aiDiarybackend.service.DiaryImageService;
import com.momeokji.aiDiarybackend.service.OpenAiService;

/**
 * 일기 API 통합 테스트 베이스. OpenAI 와 S3 업로드를 mock 하고, 원하는 {@code createdAt} 으로 일기를 심는 헬퍼를 둔다.
 */
public abstract class DiaryTestSupport extends IntegrationTestSupport {

	@MockitoBean
	protected OpenAiService openAiService;

	@MockitoBean
	protected DiaryImageService diaryImageService;

	@Autowired
	protected DiaryRepository diaryRepository;

	@Autowired
	protected MusicRepository musicRepository;

	/** 토큰의 회원 ID. {@code GET /users/me} 로 읽는다. */
	protected String memberIdOf(String token) throws Exception {
		String body = mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(body).get("memberId").asText();
	}

	/** 원하는 {@code createdAt} 으로 일기를 심는다. 서비스가 시각을 직접 넣는 구조라 빌더로 바로 저장된다. */
	protected Diary insertDiary(String memberId, LocalDateTime createdAt) {
		return insertDiary(memberId, createdAt, DiaryType.FREE);
	}

	protected Diary insertDiary(String memberId, LocalDateTime createdAt, DiaryType type) {
		return insertDiary(memberId, createdAt, type, null);
	}

	protected Diary insertDiary(String memberId, LocalDateTime createdAt, DiaryType type, Long musicId) {
		return diaryRepository.save(Diary.builder()
			.userId(memberId)
			.type(type)
			.recommandMusic(musicId)
			.createdAt(createdAt)
			.content("본문")
			.summary("요약")
			.build());
	}

	/** 일기 생성·확정 경로가 부르는 OpenAI 프롬프트의 기본 응답. 색상은 아직 구형(문자열) 형식이다. */
	protected void stubOpenAi() {
		when(openAiService.call(eq("summary"), any())).thenReturn("{\"summary\":\"요약\"}");
		when(openAiService.call(eq("colors"), any())).thenReturn("{\"colors\":\"#C3E9A6, #FFFFFF\"}");
		when(openAiService.call(eq("music"), any())).thenReturn("{\"mood\":\"none\"}");
		when(openAiService.call(eq("content"), any())).thenReturn("생성된 일기");
	}
}
