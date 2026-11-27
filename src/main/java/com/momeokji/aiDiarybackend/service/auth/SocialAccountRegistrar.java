package com.momeokji.aiDiarybackend.service.auth;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.entity.MemberTermsAgreement;
import com.momeokji.aiDiarybackend.entity.SocialAccount;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.repository.MemberTermsAgreementRepository;
import com.momeokji.aiDiarybackend.repository.SocialAccountRepository;
import com.momeokji.aiDiarybackend.service.auth.social.SocialIdentity;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 계정 행에 대한 짧은 쓰기 트랜잭션 모음. {@link SocialLoginService} 가 소셜 검증과 애플 HTTP 호출을
 * DB 트랜잭션 밖에서 하기 위해 분리했다.
 */
@Component
@RequiredArgsConstructor
public class SocialAccountRegistrar {

	private final MemberRepository memberRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final MemberTermsAgreementRepository memberTermsAgreementRepository;

	/**
	 * 새 회원, 소셜 계정, 약관 동의 기록을 함께 만든다. 독립 트랜잭션이라 유니크 위반(동시 첫 로그인)으로 실패해도
	 * 호출자가 재조회로 복구할 수 있고, 그때 이 시도의 회원과 동의 기록도 같이 롤백된다.
	 *
	 * @param terms 필수 동의 검증을 통과한 약관. 동의 기록 없는 회원이 생기지 않도록 가입과 한 트랜잭션에 둔다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SocialAccount register(SocialIdentity identity, SignupTerms terms) {
		Member member = memberRepository.save(Member.create(UUID.randomUUID().toString(), terms.notificationOptIn()));
		LocalDateTime now = LocalDateTime.now();
		memberTermsAgreementRepository.saveAll(terms.agreedTermsIds().stream()
			.map(termsId -> MemberTermsAgreement.of(member.getMemberId(), termsId, now))
			.toList());
		return socialAccountRepository.saveAndFlush(
			SocialAccount.link(member.getMemberId(), identity.provider(), identity.providerUserId(), identity.email()));
	}

	@Transactional
	public void updateEmailIfPresent(Long socialAccountId, String email) {
		socialAccountRepository.findById(socialAccountId)
			.ifPresent(account -> account.updateEmailIfPresent(email));
	}

	@Transactional
	public void storeAppleRefreshToken(Long socialAccountId, String encrypted) {
		socialAccountRepository.findById(socialAccountId)
			.ifPresent(account -> account.storeAppleRefreshToken(encrypted));
	}
}
