package com.momeokji.aiDiarybackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.dto.redis.ReferenceSet;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisService {

	private final StringRedisTemplate redis;
	private final ObjectMapper om = new ObjectMapper();

	// userId로 키 생성
	private String key(String userId) {
		return "aidiary:prod:refset:" + userId;
	}


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

	//새 일기로 갱신
	public void pushAndTrim(String userId, ReferenceSet rs) {
		try {
			String json = om.writeValueAsString(rs);
			String k = key(userId);
			redis.opsForList().leftPush(k, json);
			redis.opsForList().trim(k, 0, 4);
		} catch (Exception e) {
			throw new RuntimeException("Redis serialize error", e);
		}
	}
}