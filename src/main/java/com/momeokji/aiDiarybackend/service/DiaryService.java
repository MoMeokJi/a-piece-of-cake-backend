package com.momeokji.aiDiarybackend.service;

import com.momeokji.aiDiarybackend.dto.redis.ReferenceSet;
import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryConfirmResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryDetailResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryGenerateResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryListResponseDto;
import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.DiaryColor;
import com.momeokji.aiDiarybackend.entity.DiaryImage;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.entity.Music;
import com.momeokji.aiDiarybackend.repository.DiaryColorRepository;
import com.momeokji.aiDiarybackend.repository.DiaryImageRepository;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.repository.MusicRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
	private final MusicRepository musicRepository;
	private final MusicService musicService;
	private final DiaryImageRepository diaryImageRepository;
	private final DiaryImageService diaryImageService;

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


	//질문 일기
	@Transactional
	public DiaryConfirmResponseDto confirm(Authentication auth, String text, List<MultipartFile> imageFiles) {
		return doConfirm(auth, text, imageFiles, true);
	}

	//자유 일기
	@Transactional
	public DiaryConfirmResponseDto confirmFree(Authentication auth, String text, List<MultipartFile> imageFiles) {
		return doConfirm(auth, text, imageFiles, false);
	}


	private DiaryConfirmResponseDto doConfirm(Authentication auth, String text, List<MultipartFile> imageFiles,boolean updateRedis) {
		final String userId = auth.getName();

		// 일기 요약 ,색상 생성
		String summaryJson = openAiService.call("summary", Map.of("text", text));
		String colorsJson  = openAiService.call("colors",  Map.of("text", text));

		String summary = diarySummaryService.parseSummary(summaryJson);
		List<String> colors = diaryColorService.parseHexColors(colorsJson);

		// 음악 추천
		Music picked = musicService.pickRandomMusicByDiaryText(text);
		Long musicId = (picked != null ? picked.getId() : null);

		// 일기 파일 저장
		Member member = memberRepository.findById(userId).orElseThrow();
		Diary diary = diaryRepository.save(
			Diary.builder()
				.userId(member.getMemberId())
				.content(text)
				.summary(summary)
				.recommandMusic(musicId)
				.build()
		);

		// 색상 저장
		for (int i = 0; i < Math.min(2, colors.size()); i++) {
			diaryColorRepository.save(
				DiaryColor.builder()
					.diaryId(diary.getDiaryId())
					.colorId(i)
					.colorName(colors.get(i))
					.build()
			);
		}

		// s3에 이미지 저장 후 s3 url db에 저장
		List<String> imageUrls = new ArrayList<>();
		int idx = 0;
		for (MultipartFile file : imageFiles) {
			if (file == null || file.isEmpty()) continue;
			String url = diaryImageService.compressAndUpload(userId, diary.getDiaryId(), file);
			imageUrls.add(url);

			diaryImageRepository.save(
				DiaryImage.builder()
					.diaryId(diary.getDiaryId())
					.imageId(idx++)
					.imageUrl(url)
					.build()
			);
		}

		//자유일기에서는 redis갱신 x
		if (updateRedis) {
			redisService.finalizeLatestDiary(userId, text);
		}

		// 음악 dto생성
		DiaryConfirmResponseDto.MusicDto musicDto = null;
		if (picked != null) {
			musicDto = DiaryConfirmResponseDto.MusicDto.builder()
				.title(picked.getTitle())
				.artist(picked.getArtist())
				.videoId(picked.getVideoId())
				.build();
		}

		// 응답 반환
		return DiaryConfirmResponseDto.builder()
			.diaryId(diary.getDiaryId())
			.content(diary.getContent())
			.createdAt(diary.getCreatedAt())
			.summary(summary)
			.images(imageUrls)
			.colors(colors)
			.music(musicDto)
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

		Page<Diary> pageData = diaryRepository.findByUserIdAndIsValidTrue(userId, pageable);
		List<Diary> diaries = pageData.getContent();

		// 색상 한 번에 조회 후 매핑
		List<Long> diaryIds = diaries.stream().map(Diary::getDiaryId).toList();
		Map<Long, List<String>> colorMap = diaryIds.isEmpty()
			? Map.of()
			: diaryColorRepository.findByDiaryIdInOrderByDiaryIdAscColorIdAsc(diaryIds).stream()
			.collect(Collectors.groupingBy(
				DiaryColor::getDiaryId,
				LinkedHashMap::new,
				Collectors.mapping(DiaryColor::getColorName, Collectors.toList())
			));

		// 음악 한번에 조회 후 매핑
		List<Long> musicIds = diaries.stream()
			.map(Diary::getRecommandMusic)
			.filter(id -> id != null)
			.distinct()
			.toList();

		Map<Long, Music> musicMap = musicIds.isEmpty()
			? Map.of()
			: musicRepository.findByIdIn(musicIds).stream()
			.collect(Collectors.toMap(Music::getId, m -> m));


		// DTO 매핑
		return diaries.stream().map(d -> {
			Music m = (d.getRecommandMusic() != null) ? musicMap.get(d.getRecommandMusic()) : null;

			DiaryListResponseDto.MusicDto musicDto = null;
			if (m != null) {
				musicDto = DiaryListResponseDto.MusicDto.builder()
					.title(m.getTitle())
					.artist(m.getArtist())
					.build();
			}

			return DiaryListResponseDto.builder()
				.diaryId(d.getDiaryId())
				.summary(d.getSummary())
				.createdAt(d.getCreatedAt())
				.colors(colorMap.getOrDefault(d.getDiaryId(), List.of()))
				.music(musicDto)
				.build();
		}).toList();
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
			.findByUserIdAndIsValidTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
				userId, from, to
			);

		// 색상 일괄 로딩
		List<Long> diaryIds = diaries.stream().map(Diary::getDiaryId).toList();
		Map<Long, List<String>> colorMap = diaryIds.isEmpty()
			? Map.of()
			: diaryColorRepository.findByDiaryIdInOrderByDiaryIdAscColorIdAsc(diaryIds).stream()
			.collect(Collectors.groupingBy(
				DiaryColor::getDiaryId,
				LinkedHashMap::new,
				Collectors.mapping(DiaryColor::getColorName, Collectors.toList())
			));

		//음악 일괄 로딩
		List<Long> musicIds = diaries.stream()
			.map(Diary::getRecommandMusic)
			.filter(id -> id != null)
			.distinct()
			.toList();

		Map<Long, Music> musicMap = musicIds.isEmpty()
			? Map.of()
			: musicRepository.findByIdIn(musicIds).stream()
			.collect(Collectors.toMap(Music::getId, m2 -> m2));

		return diaries.stream().map(d -> {
			Music music = (d.getRecommandMusic() != null) ? musicMap.get(d.getRecommandMusic()) : null;

			DiaryListResponseDto.MusicDto musicDto = null;
			if (music != null) {
				musicDto = DiaryListResponseDto.MusicDto.builder()
					.title(music.getTitle())
					.artist(music.getArtist())
					.build();
			}

			// 응답 DTO 매핑
			return DiaryListResponseDto.builder()
				.diaryId(d.getDiaryId())
				.summary(d.getSummary())
				.createdAt(d.getCreatedAt())
				.colors(colorMap.getOrDefault(d.getDiaryId(), List.of()))
				.music(musicDto)
				.build();
		}).toList();

	}


	@Transactional(readOnly = true)
	public DiaryDetailResponseDto getDetail(Authentication auth, Long diaryId) {
		final String userId = auth.getName();

		Diary diary = diaryRepository.findByDiaryIdAndUserIdAndIsValidTrue(diaryId, userId)
			.orElseThrow(() -> new IllegalArgumentException("일기를 찾을 수 없습니다."));

		// 색상 일괄 조회
		List<String> colors = diaryColorRepository
			.findByDiaryIdOrderByColorIdAsc(diary.getDiaryId())
			.stream()
			.map(DiaryColor::getColorName)
			.toList();

		List<String> images = diaryImageRepository.findByDiaryIdOrderByImageIdAsc(diary.getDiaryId())
			.stream()
			.filter(img -> Boolean.TRUE.equals(img.getIsValid()))
			.map(DiaryImage::getImageUrl)
			.toList();

		DiaryDetailResponseDto.MusicDto musicDto = null;

		if (diary.getRecommandMusic() != null) {
			musicDto = musicRepository.findById(diary.getRecommandMusic())
				.map(m -> DiaryDetailResponseDto.MusicDto.builder()
					.title(m.getTitle())
					.artist(m.getArtist())
					.videoId(m.getVideoId())
					.build()
				)
				.orElse(null);
		}

		String feedback = diary.getFeedbackMsg();

		return DiaryDetailResponseDto.builder()
			.diaryId(diary.getDiaryId())
			.content(diary.getContent())
			.createdAt(diary.getCreatedAt())
			.images(images)
			.colors(colors)
			.music(musicDto)
			.feedbackMsg(feedback)
			.build();
	}

	@Transactional
	public void deleteDiary(Authentication auth, Long diaryId) {

		String userId = auth.getName();

		log.info("delete request: diaryId={}, tokenUserId={}", diaryId, userId);
		diaryRepository.findByDiaryIdAndUserIdAndIsValidTrue(diaryId, userId)
			.orElseThrow(() -> new IllegalArgumentException("일기를 찾을 수 없습니다."));

		LocalDateTime now = LocalDateTime.now();

		diaryColorRepository.softDeleteByDiaryId(diaryId, now);
		diaryImageRepository.softDeleteByDiaryId(diaryId, now);

		int updated = diaryRepository.softDeleteOne(userId, diaryId, now);

		if (updated == 0) {
			throw new IllegalStateException("이미 삭제되었거나 권한이 없습니다.");
		}
	}

}