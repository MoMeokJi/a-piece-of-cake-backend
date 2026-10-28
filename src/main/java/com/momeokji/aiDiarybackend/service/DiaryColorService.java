package com.momeokji.aiDiarybackend.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OpenAI 색상 프롬프트 응답 파서.
 *
 * <p>프롬프트가 바뀌기 전후를 모두 받는다.
 * <ol>
 *   <li>새 형식: {@code {"colors":[{"name":"새싹 연두","hex":"#C3E9A6"}, ...]}} → 이름과 hex</li>
 *   <li>구형: {@code {"colors":"#AABBCC, #DDEEFF"}} → 이름 null</li>
 *   <li>그 외 hex 가 섞인 문자열 → 이름 null</li>
 * </ol>
 */
@Service
public class DiaryColorService {

	/** 색 하나. {@code name} 은 프롬프트가 안 주면 null, {@code hex} 는 항상 대문자 {@code #RRGGBB}. */
	public record ColorPick(String name, String hex) {
	}

	private static final Pattern HEX_PATTERN = Pattern.compile("#[0-9A-Fa-f]{6}");
	private static final Pattern EXACT_HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
	private final ObjectMapper mapper = new ObjectMapper();

	public List<ColorPick> parseColors(String raw) {
		if (raw == null) throw new IllegalStateException("colors 응답이 null입니다.");

		String s = stripCodeFence(raw);

		String candidate = s;
		try {
			JsonNode colorsNode = mapper.readTree(s).get("colors");
			if (colorsNode != null && colorsNode.isArray()) {
				List<ColorPick> picks = fromObjects(colorsNode);
				if (!picks.isEmpty()) {
					return picks;
				}
			} else if (colorsNode != null && !colorsNode.isNull()) {
				candidate = colorsNode.asText();
			}
		} catch (IOException ignore) {
			// JSON 이 아니면 문자열 전체에서 hex 를 찾는다
		}

		List<ColorPick> out = new ArrayList<>();
		Matcher m = HEX_PATTERN.matcher(candidate);
		while (m.find()) {
			out.add(new ColorPick(null, m.group().toUpperCase()));
		}
		if (out.isEmpty()) {
			throw new IllegalStateException("유효한 HEX를 찾지 못했습니다: " + raw);
		}
		return out;
	}

	/** 배열 원소 중 {@code hex} 가 정확히 {@code #RRGGBB} 인 것만 채택한다. 이름이 비어 있으면 null. */
	private static List<ColorPick> fromObjects(JsonNode array) {
		List<ColorPick> picks = new ArrayList<>();
		for (JsonNode node : array) {
			String hex = node.path("hex").asText(null);
			if (hex == null || !EXACT_HEX.matcher(hex.trim()).matches()) {
				continue;
			}
			String name = node.path("name").asText(null);
			picks.add(new ColorPick(name == null || name.isBlank() ? null : name.trim(), hex.trim().toUpperCase()));
		}
		return picks;
	}

	private static String stripCodeFence(String raw) {
		return raw.trim()
			.replaceAll("^```[a-zA-Z]*\\s*", "")
			.replaceAll("\\s*```$", "")
			.trim();
	}
}
