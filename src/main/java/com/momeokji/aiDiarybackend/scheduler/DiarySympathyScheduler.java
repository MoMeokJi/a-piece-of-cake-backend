package com.momeokji.aiDiarybackend.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.service.OpenAiService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiarySympathyScheduler {

	private final DiaryRepository diaryRepository;
	private final OpenAiService openAiService;

	//10분 간격으로 공감메시지 생성
	@Scheduled(initialDelay = 600000,fixedDelay = 600000) // 600000 ms = 10분
	@Transactional
	public void fillFeedbackMessages() {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

		List<Diary> diaries = diaryRepository
			.findTop100ByIsValidTrueAndFeedbackMsgIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(threshold);

		if (diaries.isEmpty()) {
			return;
		}

		log.info("공감알림 생성 대상 일기 수 = {}", diaries.size());

		for (Diary diary : diaries) {
			if (diary.getFeedbackMsg() != null) {
				continue;
			}
			try {
				Map<String, Object> input = Map.of("text", diary.getContent());

				String sympathy = openAiService.call("sympathy", input);

				diary.updateFeedbackMsg(sympathy);

				log.info("공감알림 생성 완료 diaryId={}", diary.getDiaryId());
			} catch (Exception e) {
				log.error("공감알림 생성 실패 diaryId={}", diary.getDiaryId(), e);
			}
		}
	}
}
