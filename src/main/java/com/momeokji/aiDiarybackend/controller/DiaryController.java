package com.momeokji.aiDiarybackend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.momeokji.aiDiarybackend.dto.request.DiaryConfirmRequestDto;
import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DailyQuestionsResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryConfirmResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryDetailResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryGenerateResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryListResponseDto;
import com.momeokji.aiDiarybackend.service.DiaryService;
import com.momeokji.aiDiarybackend.service.QuestionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/diaries")
@RequiredArgsConstructor
public class DiaryController {

	private final DiaryService diaryService;
	private final QuestionService questionService;

	@PostMapping("/qna")
	public ResponseEntity<DiaryGenerateResponseDto> generateDiary(Authentication auth, @Validated @RequestBody DiaryGenerateRequestDto req) {
		return ResponseEntity.ok(diaryService.generate(auth, req));
	}

	@GetMapping("/question")
	public ResponseEntity<DailyQuestionsResponseDto> getDailyQuestions() {
		return ResponseEntity.ok(questionService.getQuestion());
	}

	@PostMapping
	public ResponseEntity<DiaryConfirmResponseDto> confirm(
		Authentication auth,
		@Validated @RequestBody DiaryConfirmRequestDto req) {

		DiaryConfirmResponseDto response = diaryService.confirm(auth, req);
		return ResponseEntity.ok(response);
	}

	@GetMapping
	public List<DiaryListResponseDto> list(
		Authentication auth,
		@RequestParam(defaultValue = "recent") String sort,
		@RequestParam(defaultValue = "0") Integer page,
		@RequestParam(defaultValue = "10") Integer size
	) {
		return diaryService.getList(auth, sort, page, size);
	}

	@GetMapping("/calendar")
	public ResponseEntity<List<DiaryListResponseDto>> getCalendar(
		Authentication auth,
		@RequestParam(required = false) Integer year,
		@RequestParam(required = false) Integer month) {

		List<DiaryListResponseDto> result = diaryService.getCalendar(auth, year, month);
		return ResponseEntity.ok(result);
	}


	@GetMapping("/{diaryId}")
	public ResponseEntity<DiaryDetailResponseDto> getDetail(
		Authentication auth,
		@PathVariable Long diaryId) {
		return ResponseEntity.ok(diaryService.getDetail(auth, diaryId));
	}

}
