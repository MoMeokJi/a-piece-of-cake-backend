package com.momeokji.aiDiarybackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.dto.redis.ReferenceSet;
import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisService {

	private static final int MAX_LIST = 5;

	private final StringRedisTemplate redis;
	private final ObjectMapper om = new ObjectMapper();

	// userId로 키 생성
	private String key(String userId) {
		return "aidiary:prod:refset:" + userId;
	}

	//ref_set조회
	public List<ReferenceSet> getAllRefSets(String userId) {
		List<String> raw = redis.opsForList().range(key(userId), 0, -1);
		List<ReferenceSet> out = new ArrayList<>();
		if (raw == null) {
			return out;
		}
		for (String s : raw) {
			try {
				out.add(om.readValue(s, new TypeReference<ReferenceSet>(){}));
			} catch (Exception ignore) {}
		}
		return out;
	}

	//Qna값 삽입
	public void pushQna(String userId, List<DiaryGenerateRequestDto.Qna> qna) {
		try {
			ReferenceSet draft = ReferenceSet.builder()
				.qna(qna)
				.diary("")                         //일기는 확정 아니므로 더미
				.createdAt(System.currentTimeMillis())
				.build();

			String json = om.writeValueAsString(draft);
			String k = key(userId);

			redis.opsForList().leftPush(k, json);
			redis.opsForList().trim(k, 0, MAX_LIST - 1);

		} catch (Exception e) {
			throw new RuntimeException("Redis db qna삽입 오류", e);
		}
	}

	//가장 최근 일기에 diary 확정해서 삽입
	public void finalizeLatestDiary(String userId, String diaryText) {
		String k = key(userId);

		//맨 앞 조회
		List<String> head = redis.opsForList().range(k, 0, 0);
		if (head == null || head.isEmpty()) {
			throw new IllegalStateException("확정할 초안이 없습니다. 먼저 문답일기를 생성해 주세요.");
		}
		try {
			ReferenceSet latest = om.readValue(head.get(0), new TypeReference<ReferenceSet>() {});
			if (latest.getDiary() != null && !latest.getDiary().isBlank()) {
				throw new IllegalStateException("가장 최근 항목이 이미 확정되어 있습니다.");
			}

			ReferenceSet finalized = ReferenceSet.builder()
				.qna(latest.getQna())
				.diary(diaryText)
				.createdAt(System.currentTimeMillis())
				.build();

			String updated = om.writeValueAsString(finalized);
			redis.opsForList().set(k, 0, updated);

		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException("Redis db ref일기 확정 오류", e);
		}
	}

	public void seedDefaultsIfEmpty(String userId, List<String> seedJsonItems) {
		String k = key(userId);
		Long size = redis.opsForList().size(k);
		if (size != null && size > 0) {
			return; // 이미 refSet이 존재하면 스킵하도록
		}

		if (seedJsonItems == null || seedJsonItems.isEmpty()) return;

		// 파일의 배열 순서를 그대로 유지
		redis.opsForList().rightPushAll(k, seedJsonItems);
		// MAX_LIST 제한
		redis.opsForList().trim(k, 0, MAX_LIST - 1);
	}

}