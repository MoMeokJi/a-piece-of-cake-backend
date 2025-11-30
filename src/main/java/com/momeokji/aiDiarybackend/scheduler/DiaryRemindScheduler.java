package com.momeokji.aiDiarybackend.scheduler;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.momeokji.aiDiarybackend.entity.Diary;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryRemindScheduler {

	private final MemberRepository memberRepository;
	private final DiaryRepository diaryRepository;
	private final FcmService fcmService;

	@Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
	@Transactional(readOnly = true)
	public void sendRemindNotifications() {
		ZoneId kst = ZoneId.of("Asia/Seoul");
		LocalDate today = LocalDate.now(kst);

		List<Member> members = memberRepository.findByIsValidTrue();
		log.info("REMIND 체크 대상 회원 수 = {}", members.size());

		for (Member member : members) {
			String memberId = member.getMemberId();

			// FCM 토큰 없으면 스킵
			String deviceId = member.getDeviceId();
			if (deviceId == null || deviceId.isBlank()) {
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

			// 2일 이상, 14일 이하 인 사람한테만 알림 보냄
			if (days < 2 || days > 14) {
				continue;
			}

			try {
				fcmService.remindNotification(memberId);
				log.info("REMIND 알림 발송 완료 memberId={}, daysSinceLastDiary={}",
					memberId, days);
			} catch (FirebaseMessagingException e) {
				log.error("REMIND 알림 발송 실패 memberId={}", memberId, e);
			}
		}
	}
}
