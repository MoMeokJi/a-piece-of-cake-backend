package com.momeokji.aiDiarybackend.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.momeokji.aiDiarybackend.dto.response.DiaryPageResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryPatchResponseDto;
import com.momeokji.aiDiarybackend.service.DiaryService;
import com.momeokji.aiDiarybackend.service.QuestionService;

import io.swagger.v3.oas.annotations.Operation;
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

	@Operation(summary = "문답으로 일기 생성", description = """
		`target_set` 의 각 항목은 `question`, `answer` 모두 필수(빈 문자열 불가)다.
		답하지 않은 문항은 **빼고** 보낸다. 전부 건너뛰었으면 앱이 이 API 를 호출하지 않는다.
		오늘(KST) 작성 개수가 제한에 도달했으면 OpenAI 호출 전에 429 `DIARY_DAILY_LIMIT_EXCEEDED` 를 돌려준다.
		""")
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
		@RequestPart(value = "images",required = false) List<MultipartFile> images
	) {
		if (!StringUtils.hasText(text)) {
			throw new IllegalArgumentException("text는 필수입니다.");
		}
		if (images != null && images.size()>4) {
			throw new IllegalArgumentException("이미지 파일은 4개 이하여야 합니다.");
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
		@RequestPart(value = "images",required = false) List<MultipartFile> images
	) {
		if (!StringUtils.hasText(text)) {
			throw new IllegalArgumentException("text는 필수입니다.");
		}
		if (images != null && images.size()>4) {
			throw new IllegalArgumentException("이미지 파일은 4개 이하여야 합니다.");
		}

		DiaryConfirmResponseDto res = diaryService.confirmFree(auth, text, images);

		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(res.getDiaryId())
			.toUri();

		return ResponseEntity.created(location).body(res);
	}



	/** 페이지 목록. sort=recent(기본)|oldest, created 는 oldest 의 별칭. */
	@Operation(summary = "일기 페이지 목록", description = """
		`sort`: `recent`(최신순, 기본) | `oldest`(오래된순). `created` 는 `oldest` 의 별칭. 그 외 값은 400 `BAD_REQUEST`.
		응답은 `items` 와 페이지 메타(`page`, `size`, `hasNext`, `totalCount`)를 담은 객체다.
		""")
	@GetMapping
	public DiaryPageResponseDto list(
		Authentication auth,
		@RequestParam(defaultValue = "recent") String sort,
		@RequestParam(defaultValue = "0") Integer page,
		@RequestParam(defaultValue = "10") Integer size
	) {
		return diaryService.getList(auth, sort, page, size);
	}

	/** 월 단위 전체 목록. year/month 생략 시 KST 이번 달. sort 는 목록과 같다. */
	@Operation(summary = "월 단위 일기 목록", description = """
		`year`/`month` 를 생략하면 KST 기준 이번 달. 월 전체를 배열로 주며 페이지 메타는 없다.
		`sort` 는 `recent`(기본) | `oldest`, `created` 는 `oldest` 의 별칭.
		""")
	@GetMapping("/calendar")
	public ResponseEntity<List<DiaryListResponseDto>> getCalendar(
		Authentication auth,
		@RequestParam(required = false) Integer year,
		@RequestParam(required = false) Integer month,
		@RequestParam(defaultValue = "recent") String sort
	) {
		return ResponseEntity.ok(diaryService.getCalendar(auth, year, month, sort));
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

	@PatchMapping("/{diaryId}")
	public ResponseEntity<DiaryPatchResponseDto> patchDiary(
		Authentication auth,
		@PathVariable Long diaryId,
		@Validated @RequestBody com.momeokji.aiDiarybackend.dto.request.DiaryPatchRequest req){
		DiaryPatchResponseDto res = diaryService.updateContent(auth, diaryId, req.getText());
		return ResponseEntity.ok(res);
	}

}
