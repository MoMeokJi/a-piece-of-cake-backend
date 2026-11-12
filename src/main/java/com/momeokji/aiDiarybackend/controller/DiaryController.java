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
		선택 질문을 여분으로 교체했다면 `question` 에는 화면에 실제로 보여준 문구를 넣는다.
		오늘(KST) 작성 개수가 제한에 도달했으면 OpenAI 호출 전에 429 `DIARY_DAILY_LIMIT_EXCEEDED` 를 돌려준다.
		""")
	@PostMapping("/qna")
	public ResponseEntity<DiaryGenerateResponseDto> generateDiary(Authentication auth, @Validated @RequestBody DiaryGenerateRequestDto req) {
		return ResponseEntity.ok(diaryService.generate(auth, req));
	}

	@Operation(summary = "오늘의 문답 질문", description = """
		`questions` 는 항상 5개다. 0~1번은 필수 질문이라 건너뛸 수 없고, 2~4번은 선택 질문이다.
		`optionalSpares` 는 선택 질문을 건너뛸 때 바꿔 끼울 여분이다. 0~5개이고 `questions` 와 겹치지 않는다. 없으면 빈 배열이다.
		앱은 선택 질문을 건너뛰면 `optionalSpares` 앞에서부터 하나 꺼내 그 자리에 넣고, 여분이 떨어진 뒤 건너뛴 문항은 빼고 진행한다.
		호출할 때마다 새로 무작위로 뽑으므로 작성 화면에 들어올 때 한 번만 호출한다.
		""")
	@GetMapping("/question")
	public ResponseEntity<DailyQuestionsResponseDto> getDailyQuestions() {
		return ResponseEntity.ok(questionService.getQuestion());
	}



	@Operation(summary = "문답 일기 확정 (multipart)", description = """
		`text`(필수)와 `images`(최대 4개, 선택)를 multipart 로 보낸다. `type` 은 `QNA` 로 저장된다.
		응답의 `colors` 는 `[{name, hex}]` 객체 배열이며 `name` 은 null 일 수 있다. `createdAt` 은 `+09:00` 오프셋을 붙인다.
		오늘(KST) 작성 개수가 제한에 도달했으면 OpenAI 호출 전에 429 `DIARY_DAILY_LIMIT_EXCEEDED` 를 돌려준다.
		""")
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

	@Operation(summary = "자유 일기 저장 (multipart)", description = """
		`text`(필수)와 `images`(최대 4개, 선택)를 multipart 로 보낸다. `type` 은 `FREE` 로 저장된다.
		응답 형식은 문답 확정과 같다. 오늘(KST) 작성 개수가 제한에 도달했으면 429 `DIARY_DAILY_LIMIT_EXCEEDED`.
		""")
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
