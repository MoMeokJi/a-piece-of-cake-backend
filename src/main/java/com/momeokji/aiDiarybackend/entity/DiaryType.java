package com.momeokji.aiDiarybackend.entity;

/** 일기 종류. 상세 화면의 "문답 일기" 태그에 쓴다. */
public enum DiaryType {
	/** 자유 일기 ({@code POST /diaries/free}) */
	FREE,
	/** 문답으로 생성한 일기 ({@code POST /diaries/qna} → {@code POST /diaries}) */
	QNA
}
