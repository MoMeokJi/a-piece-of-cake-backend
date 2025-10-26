package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DailyQuestionsResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryGenerateResponseDto;
import com.momeokji.aiDiarybackend.service.DiaryService;
import com.momeokji.aiDiarybackend.service.QuestionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/diaries")
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

}
