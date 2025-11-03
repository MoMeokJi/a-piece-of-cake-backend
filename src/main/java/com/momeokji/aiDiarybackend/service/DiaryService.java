package com.momeokji.aiDiarybackend.service;

import com.momeokji.aiDiarybackend.dto.redis.ReferenceSet;
import com.momeokji.aiDiarybackend.dto.request.DiaryConfirmRequestDto;
import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryConfirmResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryDetailResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryGenerateResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryListResponseDto;
import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.DiaryColor;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.DiaryColorRepository;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

	private final DiaryRepository diaryRepository;
	private final DiaryColorRepository diaryColorRepository;
	private final MemberRepository memberRepository;
	private final RedisService redisService;
	private final OpenAiService openAiService;
	private final DiarySummaryService diarySummaryService;
	private final DiaryColorService diaryColorService;


	@Transactional(readOnly = true)
	public DiaryGenerateResponseDto generate(Authentication auth, DiaryGenerateRequestDto req) {
		final String userId = auth.getName();
		log.info("일기생성, userId={}", userId);

		// Redis에서 reference_sets 조회
		List<ReferenceSet> refsets = redisService.getAllRefSets(userId);

		// OpenAI input용 Map 구성 (object -> JSON string으로 변환)
		Map<String, Object> input = new HashMap<>();
		input.put("reference_sets", refsets);
		input.put("target_set", req.getTargetSet());

		// Open Ai 호출
		String content = openAiService.call("content", input);

		// qna저장 (일기 내용은 빈 본문)
		redisService.pushQna(userId, req.getTargetSet());

		// 응답 반환
		return DiaryGenerateResponseDto.builder()
			.content(content)
			.build();
	}

	@Transactional
	public DiaryConfirmResponseDto confirm(Authentication auth, DiaryConfirmRequestDto req) {
		final String userId = auth.getName();

		//요약/색상
		String summaryDate   = openAiService.call("summary", Map.of("text", req.getText()));
		String colorsList = openAiService.call("colors",  Map.of("text", req.getText()));


		List<String> colors = diaryColorService.parseHexColors(colorsList);
		String summary = diarySummaryService.parseSummary(summaryDate);

		//Diary 저장
		Member member = memberRepository.findById(userId).orElseThrow();
		Diary diary = diaryRepository.save(
			Diary.builder()
				.userId(member.getMemberId())
				.content(req.getText())
				.summary(summary)
				.build()
		);

		// 색상 저장 (아이디 0과1로 2개)
		for (int i = 0; i < Math.min(2, colors.size()); i++) {
			diaryColorRepository.save(
				DiaryColor.builder()
					.diaryId(diary.getDiaryId())
					.colorId(i)
					.colorName(colors.get(i))
					.build()
			);
		}

		//음악 저장 추가해야함

		// Redis 갱신
		redisService.finalizeLatestDiary(userId, req.getText());

		// 응답 구성
		return DiaryConfirmResponseDto.builder()
			.diaryId(diary.getDiaryId())
			.content(diary.getContent())
			.createdAt(diary.getCreatedAt())
			.summary(summary)
			.images(req.getImages()) // 추후 채워야함.
			.recommandColors(colors)
			.build();
	}

	@Transactional(readOnly = true)
	public List<DiaryListResponseDto> getList(Authentication auth, String sort, Integer page, Integer size) {
		final String userId = auth.getName();

		int p = (page == null || page < 0) ? 0  : page;
		int s = (size == null || size <= 0) ? 10 : size;

		// sort=recent(기본): 최신순 desc, sort=created: 오래된순 asc
		Sort.Direction dir = "created".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
		Pageable pageable = PageRequest.of(p, s, Sort.by(dir, "createdAt"));

		Page<Diary> pageData = diaryRepository.findByUserId(userId, pageable);
		List<Diary> diaries = pageData.getContent();

		// 색상 한 번에 조회 후 매핑
		List<Long> ids = diaries.stream().map(Diary::getDiaryId).toList();
		Map<Long, List<String>> colorMap = ids.isEmpty()
			? Map.of()
			: diaryColorRepository.findByDiaryIdInOrderByDiaryIdAscColorIdAsc(ids).stream()
			.collect(Collectors.groupingBy(
				DiaryColor::getDiaryId,
				LinkedHashMap::new,
				Collectors.mapping(DiaryColor::getColorName, Collectors.toList())
			));

		return diaries.stream().map(d -> DiaryListResponseDto.builder()
			.diaryId(d.getDiaryId())
			.summary(d.getSummary())
			.createdAt(d.getCreatedAt())
			.colors(colorMap.getOrDefault(d.getDiaryId(), List.of()))
			.music(DiaryListResponseDto.MusicDto.builder()
				.title(null)         // 아직 null
				.artist("unknown")
				.build())
			.build()
		).toList();
	}

	@Transactional(readOnly = true)
	public List<DiaryListResponseDto> getCalendar(Authentication auth, Integer year, Integer month) {
		final String userId = auth.getName();

		// default 서울 기준으로 처리
		ZoneId zone = ZoneId.of("Asia/Seoul");
		ZonedDateTime nowKst = ZonedDateTime.now(zone);
		int y = (year == null || year < 1) ? nowKst.getYear()  : year;
		int m = (month == null || month < 1 || month > 12) ? nowKst.getMonthValue() : month;

		LocalDate first = LocalDate.of(y, m, 1);
		LocalDateTime from = first.atStartOfDay();             // 포함
		LocalDateTime to   = first.plusMonths(1).atStartOfDay(); // 미포함

		// 다이어리 조회 (오래된순)
		List<Diary> diaries = diaryRepository
			.findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
				userId, from, to
			);

		// 색상 일괄 로딩
		List<Long> ids = diaries.stream().map(Diary::getDiaryId).toList();
		Map<Long, List<String>> colorMap = ids.isEmpty()
			? Map.of()
			: diaryColorRepository.findByDiaryIdInOrderByDiaryIdAscColorIdAsc(ids).stream()
			.collect(Collectors.groupingBy(
				DiaryColor::getDiaryId,
				LinkedHashMap::new,
				Collectors.mapping(DiaryColor::getColorName, Collectors.toList())
			));

		// 응답 DTO 매핑
		return diaries.stream()
			.map(d -> DiaryListResponseDto.builder()
				.diaryId(d.getDiaryId())
				.summary(d.getSummary())
				.createdAt(d.getCreatedAt())
				.colors(colorMap.getOrDefault(d.getDiaryId(), List.of()))
				.music(DiaryListResponseDto.MusicDto.builder()
					.title(null)     // 아직 null
					.artist("unknown")
					.build())
				.build())
			.toList();
	}


	@Transactional(readOnly = true)
	public DiaryDetailResponseDto getDetail(Authentication auth, Long diaryId) {
		final String userId = auth.getName();

		Diary diary = diaryRepository.findByDiaryIdAndUserId(diaryId, userId)
			.orElseThrow(() -> new IllegalArgumentException("일기를 찾을 수 없습니다."));

		// 색상 일괄 조회
		List<String> colors = diaryColorRepository
			.findByDiaryIdOrderByColorIdAsc(diary.getDiaryId())
			.stream()
			.map(DiaryColor::getColorName)
			.toList();

		// TODO: DiaryImage 테이블 연동 시 채우기
		List<String> images = List.of();

		// 음악은 아직 미 구현이라 다 null값
		DiaryDetailResponseDto.MusicDto music = DiaryDetailResponseDto.MusicDto.builder()
			.title(null)
			.artist("unknown")
			.videoId(null)
			.build();

		// TODO: diary.getFeedbackMsg() 존재 시 치환
		String feedback = null;

		return DiaryDetailResponseDto.builder()
			.diaryId(diary.getDiaryId())
			.content(diary.getContent())
			.createdAt(diary.getCreatedAt())
			.images(images)
			.colors(colors)
			.music(music)
			.feedbackMsg(feedback)
			.build();
	}

}