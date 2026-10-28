package com.momeokji.aiDiarybackend.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.service.FcmService;
import com.momeokji.aiDiarybackend.service.OpenAiService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 5분마다 공감(시식평) 메시지를 만든다. 메시지는 항상 저장하고, 푸시는 기록 알림을 켠 회원에게만 보낸다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiarySympathyScheduler {

	private final DiaryRepository diaryRepository;
	private final OpenAiService openAiService;
	private final FcmService fcmService;
	private final MemberRepository memberRepository;

	//5분 간격으로 공감메시지 생성
	@Scheduled(initialDelay = 300000,fixedDelay = 300000) // 300000 ms = 5분
	@Transactional
	public void fillFeedbackMessages() {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);

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

				// 발송 실패는 FcmService 가 로그로 처리하고 예외를 던지지 않는다
				if (notificationEnabled(diary.getUserId())) {
					fcmService.feedbackNotification(diary.getUserId(), diary.getDiaryId());
				}

			} catch (Exception e) {
				log.error("공감알림 생성 실패 diaryId={}", diary.getDiaryId(), e);
			}
		}
	}

	private boolean notificationEnabled(String memberId) {
		return memberRepository.findByMemberIdAndIsValidTrue(memberId)
			.map(Member::getNotificationEnabled)
			.map(Boolean.TRUE::equals)
			.orElse(false);
	}
}
