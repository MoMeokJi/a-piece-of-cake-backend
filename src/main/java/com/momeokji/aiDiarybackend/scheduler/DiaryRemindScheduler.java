package com.momeokji.aiDiarybackend.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberDeviceRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.service.FcmService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 22시(KST) 리마인드 푸시. 기록 알림을 켠 유효 회원 중 푸시 가능한 기기가 있고, 마지막 일기가 1~14일 전인 사람에게 보낸다.
 *
 * <p>읽기 전용 트랜잭션을 걸지 않는다 — 발송 중 무효 토큰 정리(UPDATE)가 일어날 수 있기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryRemindScheduler {

	private final MemberRepository memberRepository;
	private final MemberDeviceRepository memberDeviceRepository;
	private final DiaryRepository diaryRepository;
	private final FcmService fcmService;

	@Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
	public void sendRemindNotifications() {
		ZoneId kst = ZoneId.of("Asia/Seoul");
		LocalDate today = LocalDate.now(kst);

		List<Member> members = memberRepository.findByIsValidTrueAndNotificationEnabledTrue();
		// 회원마다 exists 쿼리를 날리는 대신 푸시 가능한 회원 ID 를 한 번에 읽어 거른다.
		Set<String> pushable = memberDeviceRepository.findMemberIdsWithFcmToken();
		log.info("REMIND 체크 대상 회원 수 = {}, 푸시 가능 회원 수 = {}", members.size(), pushable.size());

		for (Member member : members) {
			String memberId = member.getMemberId();

			// 푸시 가능한 기기가 없으면 스킵
			if (!pushable.contains(memberId)) {
				continue;
			}

			Optional<Diary> lastDiaryOpt =
				diaryRepository.findTop1ByUserIdAndIsValidTrueOrderByCreatedAtDesc(memberId);

			//일기 한번도 안쓴 사람은 알림 x
			if (lastDiaryOpt.isEmpty()) {
				continue;
			}

			Diary lastDiary = lastDiaryOpt.get();
			LocalDate lastDate = lastDiary.getCreatedAt()
				.atZone(kst)
				.toLocalDate();

			long days = ChronoUnit.DAYS.between(lastDate, today);

			// 1일 이상, 14일 이하 인 사람한테만 알림 보냄
			if (days < 1 || days > 14) {
				continue;
			}

			fcmService.remindNotification(memberId);
			log.info("REMIND 알림 발송 처리 memberId={}, daysSinceLastDiary={}", memberId, days);
		}
	}
}
