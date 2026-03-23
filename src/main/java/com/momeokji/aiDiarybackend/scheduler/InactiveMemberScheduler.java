package com.momeokji.aiDiarybackend.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class InactiveMemberScheduler {

	private final MemberRepository memberRepository;
	private final AuthService authService;

	// 매일 새벽 4시 실행
	@Scheduled(cron = "0 0 4 * * *")
	public void withdrawInactiveMembers() {

		LocalDateTime time = LocalDateTime.now().minusDays(180);

		List<Member> targets = memberRepository.findByIsValidTrueAndLastActiveAtBefore(time);

		for(Member member : targets){
			try{
				authService.withdrawByUserId(member.getMemberId());
				log.info("180일 미사용 계정 탈퇴 처리 완료 memberId={}", member.getMemberId());
			}catch(Exception e){
				log.error("자동 탈퇴 실패 memberId={}", member.getMemberId(),e);
			}
		}
	}
}