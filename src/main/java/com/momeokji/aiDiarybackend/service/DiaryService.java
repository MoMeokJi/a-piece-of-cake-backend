package com.momeokji.aiDiarybackend.service;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.common.time.KstTime;
import com.momeokji.aiDiarybackend.dto.DiarySort;
import com.momeokji.aiDiarybackend.dto.redis.ReferenceSet;
import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.ColorDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryConfirmResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryDetailResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryGenerateResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryListResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryPageResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryPatchResponseDto;
import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.DiaryColor;
import com.momeokji.aiDiarybackend.entity.DiaryImage;
import com.momeokji.aiDiarybackend.entity.DiaryType;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.entity.Music;
import com.momeokji.aiDiarybackend.service.DiaryColorService.ColorPick;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
	private final DiaryStatsService diaryStatsService;
	private final Clock clock;

	@Transactional(readOnly = true)
	public DiaryGenerateResponseDto generate(Authentication auth, DiaryGenerateRequestDto req) {
		final String userId = auth.getName();
		log.info("일기생성, userId={}", userId);
		diaryStatsService.assertUnderDailyLimit(userId);

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
		return doConfirm(auth, text, imageFiles, DiaryType.QNA);
	}

	//자유 일기
	@Transactional
	public DiaryConfirmResponseDto confirmFree(Authentication auth, String text, List<MultipartFile> imageFiles) {
		return doConfirm(auth, text, imageFiles, DiaryType.FREE);
	}


	/** 문답 일기만 Redis 의 참조 세트(최근 문답·일기)를 갱신한다. 자유 일기는 문답이 없기 때문이다. */
	private DiaryConfirmResponseDto doConfirm(Authentication auth, String text, List<MultipartFile> imageFiles, DiaryType type) {
		final boolean updateRedis = type == DiaryType.QNA;
		final String userId = auth.getName();
		diaryStatsService.assertUnderDailyLimit(userId);
		String summary;

		if(text==null){
			text = "";
		}

		//이미지 파일이 null이면 빈 리스트 반환하도록 처리
		if (imageFiles == null) {
			imageFiles = List.of();
		}

		if(!updateRedis&&text.length()<80){
			summary = text;
		}
		//(text.length()>=80
		else{
			String summaryJson = openAiService.call("summary", Map.of("text", text));
			summary = diarySummaryService.parseSummary(summaryJson);
		}

		String colorsJson  = openAiService.call("colors",  Map.of("text", text));
		List<ColorPick> colors = diaryColorService.parseColors(colorsJson);

		// 음악 추천
		Music picked = musicService.pickRandomMusicByDiaryText(text);
		Long musicId = (picked != null ? picked.getId() : null);

		// 일기 파일 저장
		Member member = memberRepository.findById(userId).orElseThrow();
		Diary diary = diaryRepository.save(
			Diary.builder()
				.userId(member.getMemberId())
				.type(type)
				.createdAt(LocalDateTime.now(clock))
				.content(text)
				.summary(summary)
				.recommandMusic(musicId)
				.build()
		);

		// 색상 저장 (최대 2개)
		List<ColorDto> colorDtos = new ArrayList<>();
		for (int i = 0; i < Math.min(2, colors.size()); i++) {
			DiaryColor saved = diaryColorRepository.save(
				DiaryColor.builder()
					.diaryId(diary.getDiaryId())
					.colorId(i)
					.colorName(colors.get(i).hex())
					.name(colors.get(i).name())
					.build()
			);
			colorDtos.add(ColorDto.from(saved));
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
			redisService.finalizeLatestDiary(userId, diary.getDiaryId(), text);
		}

		// 음악 dto생성
		DiaryConfirmResponseDto.MusicDto musicDto = null;
		if (picked != null) {
			musicDto = DiaryConfirmResponseDto.MusicDto.builder()
				.title(picked.getTitle())
				.artist(picked.getArtist())
					.durationSeconds(picked.getDurationSeconds())
				.videoId(picked.getVideoId())
				.build();
		}

		// 응답 반환
		return DiaryConfirmResponseDto.builder()
			.diaryId(diary.getDiaryId())
			.type(diary.getType())
			.content(diary.getContent())
			.createdAt(KstTime.toOffset(diary.getCreatedAt()))
			.summary(summary)
			.images(imageUrls)
			.colors(colorDtos)
			.music(musicDto)
			.build();
	}

	@Transactional(readOnly = true)
	public DiaryPageResponseDto getList(Authentication auth, String sort, Integer page, Integer size) {
		final String userId = auth.getName();

		int p = (page == null || page < 0) ? 0  : page;
		int s = (size == null || size <= 0) ? 10 : size;
		Pageable pageable = PageRequest.of(p, s, DiarySort.parse(sort).byCreatedAt());

		Page<Diary> pageData = diaryRepository.findByUserIdAndIsValidTrue(userId, pageable);
		return DiaryPageResponseDto.of(pageData, toListDtos(pageData.getContent()));
	}

	/** 월 단위 전체 목록. year/month 가 없거나 범위 밖이면 KST 기준 이번 달이다. */
	@Transactional(readOnly = true)
	public List<DiaryListResponseDto> getCalendar(Authentication auth, Integer year, Integer month, String sort) {
		final String userId = auth.getName();

		LocalDate today = LocalDate.now(clock);
		int y = (year == null || year < 1) ? today.getYear()  : year;
		int m = (month == null || month < 1 || month > 12) ? today.getMonthValue() : month;

		LocalDate first = LocalDate.of(y, m, 1);
		LocalDateTime from = first.atStartOfDay();             // 포함
		LocalDateTime to   = first.plusMonths(1).atStartOfDay(); // 미포함

		List<Diary> diaries = diaryRepository.findByUserIdAndIsValidTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
			userId, from, to, DiarySort.parse(sort).byCreatedAt());
		return toListDtos(diaries);
	}

	/** 목록·캘린더 공용 매핑. 색상과 음악은 각각 한 번의 IN 조회로 가져온다. */
	private List<DiaryListResponseDto> toListDtos(List<Diary> diaries) {
		List<Long> diaryIds = diaries.stream().map(Diary::getDiaryId).toList();
		Map<Long, List<ColorDto>> colorMap = diaryIds.isEmpty()
			? Map.of()
			: diaryColorRepository.findByDiaryIdInOrderByDiaryIdAscColorIdAsc(diaryIds).stream()
			.collect(Collectors.groupingBy(
				DiaryColor::getDiaryId,
				LinkedHashMap::new,
				Collectors.mapping(ColorDto::from, Collectors.toList())
			));

		List<Long> musicIds = diaries.stream()
			.map(Diary::getRecommandMusic)
			.filter(id -> id != null)
			.distinct()
			.toList();
		Map<Long, Music> musicMap = musicIds.isEmpty()
			? Map.of()
			: musicRepository.findByIdIn(musicIds).stream()
			.collect(Collectors.toMap(Music::getId, m -> m));

		return diaries.stream().map(d -> {
			Music m = (d.getRecommandMusic() != null) ? musicMap.get(d.getRecommandMusic()) : null;
			DiaryListResponseDto.MusicDto musicDto = null;
			if (m != null) {
				musicDto = DiaryListResponseDto.MusicDto.builder()
					.title(m.getTitle())
					.artist(m.getArtist())
					.durationSeconds(m.getDurationSeconds())
					.build();
			}
			return DiaryListResponseDto.builder()
				.diaryId(d.getDiaryId())
				.type(d.getType())
				.summary(d.getSummary())
				.createdAt(KstTime.toOffset(d.getCreatedAt()))
				.colors(colorMap.getOrDefault(d.getDiaryId(), List.of()))
				.music(musicDto)
				.build();
		}).toList();
	}

	@Transactional(readOnly = true)
	public DiaryDetailResponseDto getDetail(Authentication auth, Long diaryId) {
		final String userId = auth.getName();

		Diary diary = diaryRepository.findByDiaryIdAndUserIdAndIsValidTrue(diaryId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));

		// 색상 일괄 조회
		List<ColorDto> colors = diaryColorRepository
			.findByDiaryIdOrderByColorIdAsc(diary.getDiaryId())
			.stream()
			.map(ColorDto::from)
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
					.durationSeconds(m.getDurationSeconds())
					.videoId(m.getVideoId())
					.build()
				)
				.orElse(null);
		}

		String feedback = diary.getFeedbackMsg();

		return DiaryDetailResponseDto.builder()
			.diaryId(diary.getDiaryId())
			.type(diary.getType())
			.content(diary.getContent())
			.createdAt(KstTime.toOffset(diary.getCreatedAt()))
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
			.orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));

		LocalDateTime now = LocalDateTime.now();

		diaryColorRepository.softDeleteByDiaryId(diaryId, now);
		diaryImageRepository.softDeleteByDiaryId(diaryId, now);

		int updated = diaryRepository.softDeleteOne(userId, diaryId, now);

		if (updated == 0) {
			throw new IllegalStateException("이미 삭제되었거나 권한이 없습니다.");
		}
	}

	@Transactional
	public DiaryPatchResponseDto updateContent(Authentication auth, Long diaryId, String newContent) {

		String userId = auth.getName();

		Diary diary = diaryRepository.findByDiaryIdAndUserIdAndIsValidTrue(diaryId, userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DIARY_NOT_FOUND));

		diary.updateContent(newContent);

		try {
			redisService.updateDiaryContent(userId, diaryId, newContent);
		} catch (Exception e) {
			log.warn("Redis refSet 갱신 실패 userId={}, diaryId={}", userId, diaryId, e);
		}

		return DiaryPatchResponseDto.builder()
			.diaryId(diaryId)
			.build();
	}

}