package com.momeokji.aiDiarybackend.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

/**
 * 공감(시식평) 메시지를 만든다. 메시지는 항상 저장하고, 푸시는 기록 알림을 켠 회원에게만 보낸다.
 *
 * <p>쓴 지 {@link #WAIT_MINUTES}분이 지난 일기를 2분마다 확인하므로, 일기를 쓰고 2~4분 사이에 도착한다.
 * 바로 보내면 자동 응답처럼 느껴져 일부러 뜸을 들인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiarySympathyScheduler {

	private final DiaryRepository diaryRepository;
	private final OpenAiService openAiService;
	private final FcmService fcmService;
	private final MemberRepository memberRepository;

	/** 일기를 쓴 뒤 공감 메시지를 만들기까지 기다리는 최소 시간. */
	static final int WAIT_MINUTES = 2;

	/** 실행 간격(밀리초). 2분. */
	private static final long INTERVAL_MILLIS = 2 * 60 * 1000L;

	@Scheduled(initialDelay = INTERVAL_MILLIS, fixedDelay = INTERVAL_MILLIS)
	@Transactional
	public void fillFeedbackMessages() {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(WAIT_MINUTES);

		List<Diary> diaries = diaryRepository
			.findTop100ByIsValidTrueAndFeedbackMsgIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(threshold);

		if (diaries.isEmpty()) {
			return;
		}

		log.info("공감알림 생성 대상 일기 수 = {}", diaries.size());
		Set<String> pushTargets = notificationEnabledMembers(diaries);

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
				if (pushTargets.contains(diary.getUserId())) {
					fcmService.feedbackNotification(diary.getUserId(), diary.getDiaryId());
				}

			} catch (Exception e) {
				log.error("공감알림 생성 실패 diaryId={}", diary.getDiaryId(), e);
			}
		}
	}

	/** 대상 일기의 회원을 한 번에 읽어, 유효하고 기록 알림을 켠 회원 ID 집합을 만든다. */
	private Set<String> notificationEnabledMembers(List<Diary> diaries) {
		Set<String> memberIds = diaries.stream().map(Diary::getUserId).collect(Collectors.toSet());
		return memberRepository.findByMemberIdInAndIsValidTrue(memberIds).stream()
			.filter(m -> Boolean.TRUE.equals(m.getNotificationEnabled()))
			.map(Member::getMemberId)
			.collect(Collectors.toSet());
	}
}
