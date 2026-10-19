package com.momeokji.aiDiarybackend.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

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

class SocialLoginServiceTest {

	static final String DEVICE = "11111111-1111-1111-1111-111111111111";

	GoogleIdTokenVerifier googleVerifier = mock(GoogleIdTokenVerifier.class);
	AppleIdTokenVerifier appleVerifier = mock(AppleIdTokenVerifier.class);
	SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
	SocialAccountRegistrar registrar = mock(SocialAccountRegistrar.class);
	DeviceSessionService deviceSessionService = mock(DeviceSessionService.class);
	AppleAuthClient appleAuthClient = mock(AppleAuthClient.class);
	AppleTokenCipher appleTokenCipher = mock(AppleTokenCipher.class);
	RedisService redisService = mock(RedisService.class);
	RefsetSeedLoader refsetSeedLoader = mock(RefsetSeedLoader.class);

	SocialLoginService service;

	@BeforeEach
	void setUp() {
		service = new SocialLoginService(googleVerifier, appleVerifier, socialAccountRepository, registrar,
			deviceSessionService, appleAuthClient, appleTokenCipher, redisService, refsetSeedLoader);
		when(deviceSessionService.openSession(any(), any(), any(), any()))
			.thenReturn(new IssuedTokens("a", 1800, "r", 5_184_000));
	}

	@Test
	void concurrentFirstLoginFallsBackToExistingAccount() {
		SocialIdentity identity = new SocialIdentity(SocialProvider.GOOGLE, "sub-1", "a@x.com");
		when(googleVerifier.verify("id-token")).thenReturn(identity);
		SocialAccount existing = SocialAccount.link("m-existing", SocialProvider.GOOGLE, "sub-1", "a@x.com");
		when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "sub-1"))
			.thenReturn(Optional.empty())          // 첫 조회: 없음
			.thenReturn(Optional.of(existing));    // 유니크 위반 뒤 재조회: 상대 요청이 만든 행
		when(registrar.register(identity)).thenThrow(new DataIntegrityViolationException("duplicate"));

		SocialLoginResult result = service.loginWithGoogle(request("id-token"));

		assertThat(result.newMember()).isFalse();
		verify(deviceSessionService).openSession(eq("m-existing"), eq(DEVICE), eq("IOS"), eq(null));
		verify(redisService, never()).seedDefaultsIfEmpty(any(), anyList());
	}

	@Test
	void firstLoginRegistersAndSeeds() {
		SocialIdentity identity = new SocialIdentity(SocialProvider.GOOGLE, "sub-1", null);
		when(googleVerifier.verify("id-token")).thenReturn(identity);
		when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "sub-1")).thenReturn(Optional.empty());
		when(registrar.register(identity)).thenReturn(SocialAccount.link("m-new", SocialProvider.GOOGLE, "sub-1", null));
		when(refsetSeedLoader.loadSeedItemsAsJsonStrings()).thenReturn(java.util.List.of("{}"));

		SocialLoginResult result = service.loginWithGoogle(request("id-token"));

		assertThat(result.newMember()).isTrue();
		verify(redisService).seedDefaultsIfEmpty(eq("m-new"), anyList());
		verify(deviceSessionService).openSession(eq("m-new"), eq(DEVICE), eq("IOS"), eq(null));
	}

	@Test
	void existingAccountWithEmailInTokenUpdatesEmail() {
		SocialIdentity identity = new SocialIdentity(SocialProvider.GOOGLE, "sub-1", "new@x.com");
		when(googleVerifier.verify("id-token")).thenReturn(identity);
		when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "sub-1"))
			.thenReturn(Optional.of(SocialAccount.link("m-existing", SocialProvider.GOOGLE, "sub-1", "old@x.com")));

		service.loginWithGoogle(request("id-token"));

		verify(registrar).updateEmailIfPresent(any(), eq("new@x.com"));
	}

	@Test
	void existingAccountWithoutEmailInTokenSkipsEmailUpdate() {
		SocialIdentity identity = new SocialIdentity(SocialProvider.GOOGLE, "sub-1", null);
		when(googleVerifier.verify("id-token")).thenReturn(identity);
		when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "sub-1"))
			.thenReturn(Optional.of(SocialAccount.link("m-existing", SocialProvider.GOOGLE, "sub-1", "old@x.com")));

		service.loginWithGoogle(request("id-token"));

		verify(registrar, never()).updateEmailIfPresent(any(), any());
	}

	@Test
	void existingAccountWithBlankEmailInTokenSkipsEmailUpdate() {
		SocialIdentity identity = new SocialIdentity(SocialProvider.GOOGLE, "sub-1", "  ");
		when(googleVerifier.verify("id-token")).thenReturn(identity);
		when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "sub-1"))
			.thenReturn(Optional.of(SocialAccount.link("m-existing", SocialProvider.GOOGLE, "sub-1", "old@x.com")));

		service.loginWithGoogle(request("id-token"));

		verify(registrar, never()).updateEmailIfPresent(any(), any());
	}

	@Test
	void unrelatedIntegrityViolationPropagates() {
		SocialIdentity identity = new SocialIdentity(SocialProvider.GOOGLE, "sub-1", null);
		when(googleVerifier.verify("id-token")).thenReturn(identity);
		when(socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "sub-1")).thenReturn(Optional.empty());
		when(registrar.register(identity)).thenThrow(new DataIntegrityViolationException("something else"));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.loginWithGoogle(request("id-token")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private static GoogleLoginRequestDto request(String idToken) {
		return new GoogleLoginRequestDto(idToken, DEVICE, "IOS", null);
	}
}
