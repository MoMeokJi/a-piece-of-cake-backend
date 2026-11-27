package com.momeokji.aiDiarybackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** 참조 세트(최근 문답 일기 5건)에서 삭제한 일기만 골라 지우는지. */
class RedisServiceTest {

	private static final String KEY = "aidiary:prod:refset:member-1";
	private static final String DIARY_7 = "{\"diaryId\":7,\"qna\":[{\"question\":\"q\",\"answer\":\"a\"}],\"diary\":\"일곱\",\"createdAt\":1}";
	private static final String DIARY_8 = "{\"diaryId\":8,\"qna\":[],\"diary\":\"여덟\",\"createdAt\":2}";
	/** 생성만 하고 확정하지 않은 초안과 가입 때 넣는 기본 세트에는 diaryId 가 없다. */
	private static final String DRAFT = "{\"diaryId\":null,\"qna\":[],\"diary\":\"\",\"createdAt\":3}";

	private StringRedisTemplate redis;
	private ListOperations<String, String> list;
	private RedisService redisService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redis = mock(StringRedisTemplate.class);
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

	/** 같은 일기의 항목이 둘이면(있어서는 안 되지만) 둘 다 지운다. 하나라도 남으면 OpenAI 로 계속 간다. */
	@Test
	void removesEveryEntryOfTheDeletedDiary() {
		String stale = DIARY_7.replace("일곱", "옛 본문");
		when(list.range(KEY, 0, -1)).thenReturn(List.of(DIARY_7, DIARY_8, stale));

		redisService.removeDiary("member-1", 7L);

		verify(list).remove(KEY, 0, DIARY_7);
		verify(list).remove(KEY, 0, stale);
		verify(list, never()).remove(KEY, 0, DIARY_8);
	}

	/**
	 * 본문 수정은 읽은 위치에 덮어쓰지 않고 "이 값과 똑같은 항목"을 찾아 바꾸는 스크립트로 한다.
	 * 그사이 삭제(LREM)나 새 문답(LPUSH)으로 위치가 밀려도 이웃 항목을 덮어쓰지 않는다.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void updatesContentByMatchingTheOldValueNotByIndex() {
		when(list.range(KEY, 0, -1)).thenReturn(List.of(DRAFT, DIARY_8, DIARY_7));

		redisService.updateDiaryContent("member-1", 7L, "고친 본문");

		ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
		verify(redis).execute(any(RedisScript.class), eq(List.of(KEY)), args.capture(), args.capture());
		assertThat(args.getAllValues().get(0)).isEqualTo(DIARY_7);
		assertThat((String) args.getAllValues().get(1)).contains("\"diaryId\":7", "고친 본문").doesNotContain("일곱");
		verify(list, never()).set(anyString(), anyLong(), anyString());
	}

	@Test
	void doesNothingWhenThereAreNoReferenceSets() {
		when(list.range(KEY, 0, -1)).thenReturn(null);

		redisService.removeDiary("member-1", 7L);

		verify(list, never()).remove(anyString(), anyLong(), any());
	}
}
