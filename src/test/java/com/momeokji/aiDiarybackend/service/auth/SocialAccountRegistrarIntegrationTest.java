package com.momeokji.aiDiarybackend.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.momeokji.aiDiarybackend.entity.SocialAccount;
import com.momeokji.aiDiarybackend.entity.SocialProvider;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.repository.SocialAccountRepository;
import com.momeokji.aiDiarybackend.service.auth.social.SocialIdentity;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;

/**
 * {@link SocialLoginService#resolveAccount} 의 동시 첫 로그인 복구는 두 번째 register 가
 * {@link DataIntegrityViolationException} 으로 실패한다는 가정 위에 서 있다. 실제 DB 로 그 가정을 확인한다.
 */
class SocialAccountRegistrarIntegrationTest extends IntegrationTestSupport {

	@Autowired
	SocialAccountRegistrar registrar;

	@Autowired
	SocialAccountRepository socialAccountRepository;

	@Autowired
	MemberRepository memberRepository;

	@Test
	void registeringSameIdentityTwiceViolatesUniqueConstraint() {
		SocialIdentity identity = new SocialIdentity(SocialProvider.GOOGLE, "sub-dup", "a@x.com");
		SocialAccount first = registrar.register(identity);

		assertThatThrownBy(() -> registrar.register(identity))
			.isInstanceOf(DataIntegrityViolationException.class);

		// 첫 번째 행은 그대로 남고, 두 번째 시도의 회원·계정은 REQUIRES_NEW 트랜잭션과 함께 롤백된다.
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "sub-dup"))
			.map(SocialAccount::getMemberId).contains(first.getMemberId());
		assertThat(socialAccountRepository.count()).isEqualTo(1);
		assertThat(memberRepository.count()).isEqualTo(1);
	}
}
