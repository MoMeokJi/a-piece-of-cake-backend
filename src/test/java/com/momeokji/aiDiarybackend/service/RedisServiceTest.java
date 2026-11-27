package com.momeokji.aiDiarybackend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 참조 세트(최근 문답 일기 5건)에서 삭제한 일기만 골라 지우는지. */
class RedisServiceTest {

	private static final String KEY = "aidiary:prod:refset:member-1";
	private static final String DIARY_7 = "{\"diaryId\":7,\"qna\":[{\"question\":\"q\",\"answer\":\"a\"}],\"diary\":\"일곱\",\"createdAt\":1}";
	private static final String DIARY_8 = "{\"diaryId\":8,\"qna\":[],\"diary\":\"여덟\",\"createdAt\":2}";
	/** 생성만 하고 확정하지 않은 초안과 가입 때 넣는 기본 세트에는 diaryId 가 없다. */
	private static final String DRAFT = "{\"diaryId\":null,\"qna\":[],\"diary\":\"\",\"createdAt\":3}";

	private ListOperations<String, String> list;
	private RedisService redisService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		list = mock(ListOperations.class);
		when(redis.opsForList()).thenReturn(list);
		redisService = new RedisService(redis);
	}

	@Test
	void removesOnlyTheDeletedDiary() {
		when(list.range(KEY, 0, -1)).thenReturn(List.of(DRAFT, DIARY_8, DIARY_7));

		redisService.removeDiary("member-1", 7L);

		verify(list).remove(KEY, 0, DIARY_7);
		verify(list, never()).remove(KEY, 0, DIARY_8);
		verify(list, never()).remove(KEY, 0, DRAFT);
	}

	@Test
	void doesNothingWhenTheDiaryIsNotInTheReferenceSets() {
		when(list.range(KEY, 0, -1)).thenReturn(List.of(DRAFT, DIARY_8, "not json"));

		redisService.removeDiary("member-1", 7L);

		verify(list, never()).remove(anyString(), anyLong(), any());
	}

	@Test
	void doesNothingWhenThereAreNoReferenceSets() {
		when(list.range(KEY, 0, -1)).thenReturn(null);

		redisService.removeDiary("member-1", 7L);

		verify(list, never()).remove(anyString(), anyLong(), any());
	}
}
