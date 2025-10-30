package com.momeokji.aiDiarybackend.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class DiarySummaryService {

	private final ObjectMapper mapper = new ObjectMapper();

	public String parseSummary(String summary) {
		if (summary == null) {
			return "";
		}

		try {
			JsonNode node = mapper.readTree(summary);
			JsonNode s = node.get("summary");
			if (s != null && !s.isNull()) {
				return s.asText().trim();
			}
		} catch (IOException ignore) {

		}
		String string = summary.trim();
		if (string.length() >= 2 && string.startsWith("\"") && string.endsWith("\"")) {
			string = string.substring(1, string.length() - 1).trim();
		}
		return string;
	}
}
