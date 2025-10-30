package com.momeokji.aiDiarybackend.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DiaryColorService {

	private static final Pattern HEX_PATTERN = Pattern.compile("#[0-9A-Fa-f]{6}");
	private final ObjectMapper mapper = new ObjectMapper();

	public List<String> parseHexColors(String colors) {
		if (colors == null) throw new IllegalStateException("colors 응답이 null입니다.");

		// 1) 코드펜스/라벨 제거
		String s = colors.trim()
			.replaceAll("^```[a-zA-Z]*\\s*", "") // 시작 ``` 또는 ```json 제거
			.replaceAll("\\s*```$", "")          // 끝 ```
			.trim();

		// 2) JSON 시도: {"colors":"#AABBCC, #DDEEFF"} 형태면 바로 꺼냄
		String candidate = s;
		try {
			JsonNode node = mapper.readTree(s);
			JsonNode colorsNode = node.get("colors");
			if (colorsNode != null && !colorsNode.isNull()) {
				candidate = colorsNode.asText();
			}
		} catch (IOException ignore) {
			// JSON이 아니면 candidate는 s 그대로 유지
		}

		// 3) 문자열 전체에서 HEX 토큰만 추출(코드펜스/따옴표/중괄호가 섞여 있어도 안전)
		Matcher m = HEX_PATTERN.matcher(candidate);
		List<String> out = new ArrayList<>();
		while (m.find()) out.add(m.group().toUpperCase());

		if (out.isEmpty()) {
			throw new IllegalStateException("유효한 HEX를 찾지 못했습니다: " + colors);
		}
		return out;
	}
}
