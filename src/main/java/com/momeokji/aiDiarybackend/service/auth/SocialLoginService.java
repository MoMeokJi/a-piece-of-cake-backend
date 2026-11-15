package com.momeokji.aiDiarybackend.service.auth;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.momeokji.aiDiarybackend.dto.request.AppleLoginRequestDto;
import com.momeokji.aiDiarybackend.dto.request.GoogleLoginRequestDto;
import com.momeokji.aiDiarybackend.entity.SocialAccount;
import com.momeokji.aiDiarybackend.entity.SocialProvider;
import com.momeokji.aiDiarybackend.repository.SocialAccountRepository;
import com.momeokji.aiDiarybackend.seed.RefsetSeedLoader;
import com.momeokji.aiDiarybackend.service.RedisService;
import com.momeokji.aiDiarybackend.service.auth.apple.AppleAuthClient;
import com.momeokji.aiDiarybackend.service.auth.apple.AppleTokenCipher;
import com.momeokji.aiDiarybackend.service.auth.social.AppleIdTokenVerifier;
import com.momeokji.aiDiarybackend.service.auth.social.GoogleIdTokenVerifier;
import com.momeokji.aiDiarybackend.service.auth.social.SocialIdentity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 소셜 로그인(스펙 6.1 처리 순서).
 * <ol>
 *   <li>소셜 토큰을 검증하고 {@code (provider, sub)} 를 얻는다</li>
 *   <li>소셜 계정을 찾고, 없으면 회원과 함께 만든다(동시 첫 로그인은 재조회로 흡수). 신규면 refsets 시드</li>
 *   <li>애플이면 저장된 refresh 토큰이 없을 때 code 교환을 시도한다(실패해도 로그인은 진행)</li>
 *   <li>기기 세션을 upsert 하고 토큰 한 쌍을 발급한다</li>
 * </ol>
 * 이 클래스의 메서드는 트랜잭션을 걸지 않는다 — 외부 HTTP 호출이 DB 트랜잭션 밖에서 일어나게 하기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

	private final GoogleIdTokenVerifier googleIdTokenVerifier;
	private final AppleIdTokenVerifier appleIdTokenVerifier;
	private final SocialAccountRepository socialAccountRepository;
	private final SocialAccountRegistrar registrar;
	private final DeviceSessionService deviceSessionService;
	private final AppleAuthClient appleAuthClient;
	private final AppleTokenCipher appleTokenCipher;
	private final RedisService redisService;
	private final RefsetSeedLoader refsetSeedLoader;

	public SocialLoginResult loginWithGoogle(GoogleLoginRequestDto req) {
		SocialIdentity identity = googleIdTokenVerifier.verify(req.getIdToken());
		return login(identity, req.getDeviceId(), req.getOs(), req.getFcmToken(), null);
	}

	public SocialLoginResult loginWithApple(AppleLoginRequestDto req) {
		SocialIdentity identity = appleIdTokenVerifier.verify(req.getIdentityToken(), req.getNonce());
		return login(identity, req.getDeviceId(), req.getOs(), req.getFcmToken(), req.getAuthorizationCode());
	}

	private SocialLoginResult login(SocialIdentity identity, String deviceId, String os, String fcmToken,
		String appleAuthorizationCode) {

		ResolvedAccount resolved = resolveAccount(identity);
		SocialAccount account = resolved.account();

		if (resolved.newMember()) {
			redisService.seedDefaultsIfEmpty(account.getMemberId(), refsetSeedLoader.loadSeedItemsAsJsonStrings());
		}

		if (identity.provider() == SocialProvider.APPLE && account.getAppleRefreshToken() == null
			&& appleAuthorizationCode != null) {
			exchangeAppleCode(account, appleAuthorizationCode);
		}

		IssuedTokens tokens = deviceSessionService.openSession(account.getMemberId(), deviceId, os, fcmToken);
		log.info("소셜 로그인. provider={}, memberId={}, newMember={}", identity.provider(), account.getMemberId(), resolved.newMember());
		return new SocialLoginResult(tokens, resolved.newMember());
	}

	/** 기존 계정이면 email 만 보강하고, 없으면 만든다. 동시에 만들다 유니크 위반이 나면 상대가 만든 행을 쓴다. */
	private ResolvedAccount resolveAccount(SocialIdentity identity) {
		Optional<SocialAccount> existing = find(identity);
		if (existing.isPresent()) {
			// 애플 재로그인처럼 email 이 없는 토큰이면 쓰기 트랜잭션 자체를 열지 않는다.
			if (hasText(identity.email())) {
				registrar.updateEmailIfPresent(existing.get().getId(), identity.email());
			}
			return new ResolvedAccount(existing.get(), false);
		}
		try {
			return new ResolvedAccount(registrar.register(identity), true);
		} catch (DataIntegrityViolationException e) {
			SocialAccount raced = find(identity).orElseThrow(() -> e);
			log.info("동시 첫 로그인으로 유니크 위반 → 기존 회원으로 처리. provider={}", identity.provider());
			return new ResolvedAccount(raced, false);
		}
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	private Optional<SocialAccount> find(SocialIdentity identity) {
		return socialAccountRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId());
	}

	/** 애플 refresh 토큰은 탈퇴 revoke 에 필요하다. 교환 실패는 WARN 으로 남기고 다음 로그인 때 다시 시도한다. */
	private void exchangeAppleCode(SocialAccount account, String authorizationCode) {
		appleAuthClient.exchangeAuthorizationCode(authorizationCode)
			.ifPresentOrElse(
				refreshToken -> registrar.storeAppleRefreshToken(account.getId(), appleTokenCipher.encrypt(refreshToken)),
				() -> log.warn("애플 code 교환에 실패해 refresh 토큰 없이 로그인합니다. memberId={}", account.getMemberId()));
	}

	private record ResolvedAccount(SocialAccount account, boolean newMember) {
	}
}
