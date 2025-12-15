package com.momeokji.aiDiarybackend.dto.openai;

import lombok.Getter;
import java.util.List;

@Getter
public class OpenAiResponseDto {
	private String output_text;

	private List<OutputItem> output;

	@Getter
	public static class OutputItem {
		private List<ContentItem> content;
	}
	@Getter
	public static class ContentItem {
		private String type;   // "output_text" 등
		private String text;   // 결과 텍스트
	}

}