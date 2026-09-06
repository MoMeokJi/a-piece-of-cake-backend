package com.momeokji.aiDiarybackend.controller;

import com.momeokji.aiDiarybackend.dto.request.QuestionCreateRequestDto;
import com.momeokji.aiDiarybackend.dto.request.QuestionDeleteRequestDto;
import com.momeokji.aiDiarybackend.dto.request.QuestionUpdateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.QuestionResponseDto;
import com.momeokji.aiDiarybackend.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
public class QuestionController {

	private final QuestionService questionService;

	@PostMapping
	public ResponseEntity<QuestionResponseDto> createQuestion(@Valid @RequestBody QuestionCreateRequestDto req) {
		return ResponseEntity.status(HttpStatus.CREATED).body(questionService.createQuestion(req));
	}

	@PatchMapping
	public ResponseEntity<QuestionResponseDto> updateQuestion(@Valid @RequestBody QuestionUpdateRequestDto req) {
		return ResponseEntity.ok(questionService.updateQuestion(req));
	}

	@DeleteMapping
	public ResponseEntity<Void> deleteQuestion(@Valid @RequestBody QuestionDeleteRequestDto req) {
		questionService.deleteQuestion(req.getId());
		return ResponseEntity.noContent().build();
	}

	@GetMapping
	public ResponseEntity<List<QuestionResponseDto>> getQuestions() {
		return ResponseEntity.ok(questionService.getQuestions());
	}
}