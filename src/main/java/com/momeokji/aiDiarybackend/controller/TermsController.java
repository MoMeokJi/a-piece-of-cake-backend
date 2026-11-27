package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.response.TermsListResponseDto;
import com.momeokji.aiDiarybackend.service.TermsService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

/** 가입 화면 약관 목록(공개). 경로가 {@code /auth/terms} 인 것은 {@code /terms} 가 이용약관 HTML 페이지이기 때문이다. */
@RestController
@RequiredArgsConstructor
public class TermsController {

	private final TermsService termsService;

	@Operation(summary = "가입 화면 약관 목록 (공개)", description = """
		약관 시트에 보여줄 항목을 표시 순서대로 준다. 앱은 받은 목록 그대로 그리고, `required` 가 true 인 항목이
		모두 체크됐을 때만 가입을 진행한다. 체크한 항목의 `id` 는 소셜 로그인 요청의 `agreedTermsIds` 로 보낸다.
		`contentPath` 는 본문 페이지 경로이며 API 기본 주소에 붙여 연다. null 이면 본문이 없는 항목이다.
		""")
	@GetMapping("/auth/terms")
	public ResponseEntity<TermsListResponseDto> terms() {
		return ResponseEntity.ok(TermsListResponseDto.from(termsService.activeTerms()));
	}
}
