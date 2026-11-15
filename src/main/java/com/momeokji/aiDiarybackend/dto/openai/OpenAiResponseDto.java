package com.momeokji.aiDiarybackend.dto.openai;

import lombok.Getter;
import java.util.List;

@Getter
public class OpenAiResponseDto {
	/** SDK 가 만들어 주는 편의 필드. 원본 HTTP 응답에는 보통 없다. */
	private String output_text;

	/** "completed" | "incomplete" | "failed" 등. */
	private String status;

	/** status 가 incomplete 일 때 이유. */
	private IncompleteDetails incomplete_details;

	private List<OutputItem> output;

	@Getter
	public static class IncompleteDetails {
		private String reason; // "max_output_tokens" 등
	}

	@Getter
	public static class OutputItem {
		private String type;   // "message" | "reasoning" 등. reasoning 을 켜면 reasoning 항목이 message 앞에 온다.
		private List<ContentItem> content;
	}
	@Getter
	public static class ContentItem {
		private String type;   // "output_text" 등
		private String text;   // 결과 텍스트
	}

}
