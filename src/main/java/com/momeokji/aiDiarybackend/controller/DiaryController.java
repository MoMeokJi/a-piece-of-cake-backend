package com.momeokji.aiDiarybackend.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DailyQuestionsResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryConfirmResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryDetailResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryGenerateResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryListResponseDto;
import com.momeokji.aiDiarybackend.service.DiaryService;
import com.momeokji.aiDiarybackend.service.QuestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/diaries")
@RequiredArgsConstructor
public class DiaryController {

	private final DiaryService diaryService;
	private final QuestionService questionService;
	private final ObjectMapper objectMapper;

	@PostMapping("/qna")
	public ResponseEntity<DiaryGenerateResponseDto> generateDiary(Authentication auth, @Validated @RequestBody DiaryGenerateRequestDto req) {
		return ResponseEntity.ok(diaryService.generate(auth, req));
	}

	@GetMapping("/question")
	public ResponseEntity<DailyQuestionsResponseDto> getDailyQuestions() {
		return ResponseEntity.ok(questionService.getQuestion());
	}



	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DiaryConfirmResponseDto> confirm(
		Authentication auth,
		@RequestPart("text") String text,
		@RequestPart("images") List<MultipartFile> images
	) {
		if (!StringUtils.hasText(text)) {
			throw new IllegalArgumentException("text는 필수입니다.");
		}
		if (images == null || images.isEmpty()) {
			throw new IllegalArgumentException("이미지 파일은 1개 이상이어야 합니다.");
		}

		DiaryConfirmResponseDto res = diaryService.confirm(auth, text, images);

		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(res.getDiaryId())
			.toUri();

		return ResponseEntity.created(location).body(res); // 201 + Location + 본문
	}

	@PostMapping(value = "/free", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DiaryConfirmResponseDto> confirmFree(
		Authentication auth,
		@RequestPart("text") String text,
		@RequestPart("images") List<MultipartFile> images
	) {
		if (!StringUtils.hasText(text)) {
			throw new IllegalArgumentException("text는 필수입니다.");
		}
		if (images == null || images.isEmpty()) {
			throw new IllegalArgumentException("이미지 파일은 1개 이상이어야 합니다.");
		}

		DiaryConfirmResponseDto res = diaryService.confirmFree(auth, text, images);

		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(res.getDiaryId())
			.toUri();

		return ResponseEntity.created(location).body(res);
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
	public ResponseEntity<List<DiaryListResponseDto>> getCalendar(Authentication auth, @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) {

		List<DiaryListResponseDto> result = diaryService.getCalendar(auth, year, month);
		return ResponseEntity.ok(result);
	}


	@GetMapping("/{diaryId}")
	public ResponseEntity<DiaryDetailResponseDto> getDetail(Authentication auth, @PathVariable Long diaryId) {
		return ResponseEntity.ok(diaryService.getDetail(auth, diaryId));
	}

	@DeleteMapping("/{diaryId}")
	public ResponseEntity<Void> delete(@PathVariable Long diaryId, Authentication auth) {
		diaryService.deleteDiary(auth, diaryId);
		return ResponseEntity.noContent().build();
	}

}
