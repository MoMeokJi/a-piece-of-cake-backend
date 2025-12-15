package com.momeokji.aiDiarybackend.seed;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RefsetSeedLoader {

	private static final String CLASSPATH_JSON = "static/refSet/refsets-default.json";
	private final ObjectMapper mapper = new ObjectMapper();

	//refsets-default.json의 각 element(JSON 객체)를 문자열로 반환
	public List<String> loadSeedItemsAsJsonStrings() {
		try {
			ClassPathResource res = new ClassPathResource(CLASSPATH_JSON);
			try (InputStream in = res.getInputStream()) {
				JsonNode root = mapper.readTree(in);
				if (!root.isArray()) {
					throw new IllegalStateException("refsets-default.json 은 배열이어야 합니다.");
				}
				List<String> out = new ArrayList<>();
				for (JsonNode n : root) out.add(n.toString()); // 각 원소를 그대로 String화
				return out;
			}
		} catch (Exception e) {
			throw new IllegalStateException("시드 파일 로딩 실패: " + CLASSPATH_JSON, e);
		}
	}
}
