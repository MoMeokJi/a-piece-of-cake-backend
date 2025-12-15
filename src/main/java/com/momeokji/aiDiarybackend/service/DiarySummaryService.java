package com.momeokji.aiDiarybackend.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class DiarySummaryService {

	private final ObjectMapper mapper = new ObjectMapper();

	public String parseSummary(String summary) {
		if (summary == null) return "";

		String cleaned = summary.trim();

		// 코드블록 제거 ```json ... ```
		if (cleaned.startsWith("```")) {
			cleaned = cleaned.replaceAll("```json", "")
				.replaceAll("```", "")
				.trim();
		}

		try {
			JsonNode node = mapper.readTree(cleaned);
			JsonNode s = node.get("summary");
			if (s != null && !s.isNull()) return s.asText().trim();
		} catch (IOException ignore) {}

		// JSON 파싱 실패 시 문자열 자체 반환
		if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
			cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
		}
		return cleaned;
	}
}
