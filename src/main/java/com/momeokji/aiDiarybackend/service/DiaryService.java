package com.momeokji.aiDiarybackend.service;

import static com.momeokji.aiDiarybackend.service.DiaryColorService.*;
;
import com.momeokji.aiDiarybackend.dto.redis.ReferenceSet;
import com.momeokji.aiDiarybackend.dto.request.DiaryConfirmRequestDto;
import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryConfirmResponseDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryGenerateResponseDto;
import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.DiaryColor;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.DiaryColorRepository;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

	private final DiaryRepository diaryRepository;
	private final DiaryColorRepository diaryColorRepository;
	private final MemberRepository memberRepository;
	private final RedisService redisService;
	private final OpenAiService openAiService;


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
		String summary   = openAiService.call("summary", Map.of("text", req.getText()));
		String colorsList = openAiService.call("colors",  Map.of("text", req.getText()));


		List<String> colors = parseHexColors(colorsList);

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
}