package com.momeokji.aiDiarybackend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.momeokji.aiDiarybackend.entity.SocialAccount;
import com.momeokji.aiDiarybackend.entity.SocialProvider;
import com.momeokji.aiDiarybackend.repository.DiaryColorRepository;
import com.momeokji.aiDiarybackend.repository.DiaryImageRepository;
import com.momeokji.aiDiarybackend.repository.DiaryRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.repository.MemberTermsAgreementRepository;
import com.momeokji.aiDiarybackend.repository.SocialAccountRepository;
import com.momeokji.aiDiarybackend.service.auth.DeviceSessionService;
import com.momeokji.aiDiarybackend.service.auth.apple.AppleAuthClient;
import com.momeokji.aiDiarybackend.service.auth.apple.AppleTokenCipher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 탈퇴(스펙 6.2). 로그인/토큰 발급은 {@code service.auth} 패키지에 있다.
 *
 * <ol>
 *   <li>애플 계정에 저장된 refresh 토큰이 있으면 revoke 한다(실패해도 계속)</li>
 *   <li>소셜 계정, 기기 세션, 약관 동의 기록 행을 지운다 — 이후 refresh 가 불가능해진다</li>
 *   <li>일기·색상·이미지와 회원을 soft delete 하고(회원의 닉네임과 알림 설정은 이때 지운다) Redis 유저 데이터를 지운다</li>
 * </ol>
 * 탈퇴 후 같은 소셜 계정으로 로그인하면 새 member_id 로 새로 가입된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
	private final MemberRepository memberRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final DeviceSessionService deviceSessionService;
	private final AppleAuthClient appleAuthClient;
	private final AppleTokenCipher appleTokenCipher;
	private final RedisService redisService;
	private final DiaryRepository diaryRepository;
	private final DiaryColorRepository diaryColorRepository;
	private final DiaryImageRepository diaryImageRepository;
	private final MemberTermsAgreementRepository memberTermsAgreementRepository;
	private final TransactionTemplate transactionTemplate;

	public void withdraw(Authentication auth) {
		withdrawByUserId(auth.getName());
	}

	public void withdrawByUserId(String userId) {
		// 외부 HTTP 호출(애플 revoke)은 DB 트랜잭션 밖에서 한다.
		revokeAppleTokens(userId);
		// 같은 클래스 안의 @Transactional 은 프록시를 타지 않으므로 TransactionTemplate 으로 묶는다.
		transactionTemplate.executeWithoutResult(status -> deleteAccountData(userId));
	}

	private void revokeAppleTokens(String userId) {
		for (SocialAccount account : socialAccountRepository.findByMemberId(userId)) {
			if (account.getProvider() != SocialProvider.APPLE) {
				continue;
			}
			if (account.getAppleRefreshToken() == null) {
				// 로그인 때 code 교환이 실패했던 계정이다. 애플 쪽 연결은 이용자가 기기 설정에서 직접 끊어야 한다.
				log.warn("저장된 애플 토큰이 없어 revoke 를 건너뜁니다. memberId={}", userId);
				continue;
			}
			try {
				boolean revoked = appleAuthClient.revoke(appleTokenCipher.decrypt(account.getAppleRefreshToken()));
				if (revoked) {
					log.info("애플 토큰 revoke 완료. memberId={}", userId);
				} else {
					log.error("애플 토큰 revoke 실패. 탈퇴는 계속 진행합니다. memberId={}", userId);
				}
			} catch (RuntimeException e) {
				log.error("애플 토큰 revoke 중 오류. 탈퇴는 계속 진행합니다. memberId={}", userId, e);
			}
		}
	}

	private void deleteAccountData(String userId) {
		socialAccountRepository.deleteByMemberId(userId);
		deviceSessionService.deleteAllOf(userId);
		memberTermsAgreementRepository.deleteByMemberId(userId);

		List<Long> aliveDiaryIds = diaryRepository.findAliveIdsByUserId(userId);
		LocalDateTime now = LocalDateTime.now();
		if (!aliveDiaryIds.isEmpty()) {
			diaryColorRepository.softDeleteByDiaryIdIn(aliveDiaryIds, now);
			diaryImageRepository.softDeleteByDiaryIdIn(aliveDiaryIds, now);
			diaryRepository.softDeleteAllOfUser(userId, now);
		}
		memberRepository.softDeleteOne(userId, now);
		redisService.deleteUserData(userId);
	}
}
