package com.momeokji.aiDiarybackend.dto.response;

import java.util.List;

import com.momeokji.aiDiarybackend.entity.Terms;

/**
 * {@code GET /auth/terms} 응답. 시트에 보이는 순서대로 준다.
 * 약관 종류({@code code})는 넣지 않는다 — 앱이 종류별로 분기하지 않고 받은 대로 그리게 하기 위해서다.
 */
public record TermsListResponseDto(List<Item> terms) {

	/**
	 * @param id          로그인 요청의 {@code agreedTermsIds} 에 넣는 값
	 * @param title       시트에 보이는 문구
	 * @param required    필수 여부. 필수가 모두 체크돼야 가입할 수 있다
	 * @param contentPath 본문 페이지 경로(API 기본 주소 기준). 본문이 없으면 null
	 */
	public record Item(Long id, String title, boolean required, String contentPath) {
	}

	public static TermsListResponseDto from(List<Terms> terms) {
		return new TermsListResponseDto(terms.stream()
			.map(t -> new Item(t.getId(), t.getTitle(), Boolean.TRUE.equals(t.getRequired()), t.getContentPath()))
			.toList());
	}
}
