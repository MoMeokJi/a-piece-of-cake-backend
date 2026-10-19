package com.momeokji.aiDiarybackend.service.auth;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.entity.SocialAccount;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
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

	/**
	 * 새 회원과 소셜 계정을 만든다. 독립 트랜잭션이라 유니크 위반(동시 첫 로그인)으로 실패해도
	 * 호출자가 재조회로 복구할 수 있다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public SocialAccount register(SocialIdentity identity) {
		Member member = memberRepository.save(Member.create(UUID.randomUUID().toString()));
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
