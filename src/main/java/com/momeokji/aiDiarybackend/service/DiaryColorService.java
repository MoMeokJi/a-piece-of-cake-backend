package com.momeokji.aiDiarybackend.service;

import java.util.Arrays;
import java.util.List;

public class DiaryColorService {

	public static List<String> parseHexColors(String colors) {
		try {
			// JSON으로 파싱 시도
			var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			String value;
			try {
				var node = mapper.readTree(colors);
				var colorsNode = node.get("colors");
				value = (colorsNode != null && !colorsNode.isNull()) ? colorsNode.asText() : colors;
			} catch (Exception ignore) {
				// JSON이 아니면 그대로 사용
				value = colors;
			}

			// split + 정규식 검증
			return Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.map(hex -> {
					if (!hex.matches("^#[0-9A-Fa-f]{6}$")) {
						throw new IllegalArgumentException("잘못된 HEX: " + hex);
					}
					return hex.toUpperCase();
				})
				.toList();
		} catch (Exception e) {
			throw new IllegalStateException("colors 파싱 실패: " + colors, e);
		}
	}
}
