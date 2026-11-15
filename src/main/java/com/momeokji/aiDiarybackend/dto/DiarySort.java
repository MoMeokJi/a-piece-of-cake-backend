package com.momeokji.aiDiarybackend.dto;

import org.springframework.data.domain.Sort;

/** 일기 목록·캘린더의 {@code sort} 파라미터. {@code created} 는 예전 이름이라 {@code oldest} 의 별칭으로 남긴다. */
public enum DiarySort {
	RECENT(Sort.Direction.DESC),
	OLDEST(Sort.Direction.ASC);

	public static final String MESSAGE = "sort 는 recent 또는 oldest 여야 합니다.";

	private final Sort.Direction direction;

	DiarySort(Sort.Direction direction) {
		this.direction = direction;
	}

	/** null 이나 빈 값은 {@code RECENT}. 대소문자는 구분하지 않는다. */
	public static DiarySort parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return RECENT;
		}
		return switch (raw.trim().toLowerCase()) {
			case "recent" -> RECENT;
			case "oldest", "created" -> OLDEST;
			default -> throw new IllegalArgumentException(MESSAGE);
		};
	}

	public Sort.Direction direction() {
		return direction;
	}

	public Sort byCreatedAt() {
		return Sort.by(direction, "createdAt");
	}
}
