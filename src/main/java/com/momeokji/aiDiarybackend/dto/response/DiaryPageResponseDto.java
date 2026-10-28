package com.momeokji.aiDiarybackend.dto.response;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.Builder;
import lombok.Getter;

/** {@code GET /diaries} 응답. 앱이 다음 페이지가 있는지 알 수 있도록 메타를 같이 준다. */
@Getter
@Builder
public class DiaryPageResponseDto {
	private List<DiaryListResponseDto> items;
	private int page;
	private int size;
	private boolean hasNext;
	private long totalCount;

	public static DiaryPageResponseDto of(Page<?> page, List<DiaryListResponseDto> items) {
		return DiaryPageResponseDto.builder()
			.items(items)
			.page(page.getNumber())
			.size(page.getSize())
			.hasNext(page.hasNext())
			.totalCount(page.getTotalElements())
			.build();
	}
}
