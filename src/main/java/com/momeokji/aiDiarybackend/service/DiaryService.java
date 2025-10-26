package com.momeokji.aiDiarybackend.service;

import com.momeokji.aiDiarybackend.dto.openai.OpenAiRequestDto;
import com.momeokji.aiDiarybackend.dto.redis.ReferenceSet;
import com.momeokji.aiDiarybackend.dto.request.DiaryGenerateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryGenerateResponseDto;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
	private final MemberRepository memberRepository;
	private final RedisService redisService;
	private final OpenAiService openAiService;

	@Value("${openai.prompt.id}")
	private String promptId;

	@Value("${openai.prompt.version}")
	private String promptVersion;

	@Transactional(readOnly = true)
	public DiaryGenerateResponseDto generate(Authentication auth, DiaryGenerateRequestDto req) {
		final String userId = auth.getName();
		log.info("[DRY-RUN] generate diary for userId={}", userId);

		// 1) Redis에서 reference_sets 조회
		List<ReferenceSet> refsets = redisService.getAllRefSets(userId);

		// 2) OpenAI input용 Map 구성 (object -> JSON string으로 변환)
		Map<String, Object> input = new HashMap<>();
		input.put("reference_sets", refsets);
		input.put("target_set", req.getTargetSet());

		// 3) prompt 구성
		OpenAiRequestDto.Prompt prompt = OpenAiRequestDto.Prompt.builder()
			.id(promptId)             // pmpt_xxx
			.version(promptVersion)   // "7"
			.build();

		// 4) OpenAI 호출
		String content = openAiService.call(prompt, input);

        /* =======================
           아래는 나중에 활성화 예정 (현재는 주석)
        ========================== */

		// // 4) DB 저장 (content만)
		// Member member = memberRepository.findById(userId).orElseThrow();
		// Diary diary = diaryRepository.save(
		//     Diary.builder()
		//         .userId(member.getMemberId())
		//         .content(content)
		//         .build()
		// );
		// log.info("Saved diary: id={}, userId={}", diary.getDiaryId(), diary.getUserId());

		// // 5) Redis 회전 저장(최신4 + 이번 생성1 → 5 유지)
		// ReferenceSet newRef = ReferenceSet.builder()
		//     .qna(req.getTargetSet())
		//     .diary(content)
		//     .createdAt(Instant.now().toEpochMilli())
		//     .build();
		// redisService.pushAndTrim(userId, newRef);

		// 4) 응답 반환 (AI 본문만)
		return DiaryGenerateResponseDto.builder()
			.content(content)
			.build();
	}
}