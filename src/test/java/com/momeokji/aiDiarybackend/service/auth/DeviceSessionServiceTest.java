package com.momeokji.aiDiarybackend.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.config.JwtProperties;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.entity.MemberDevice;
import com.momeokji.aiDiarybackend.repository.MemberDeviceRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.security.AccessTokenIssuer;
import com.momeokji.aiDiarybackend.security.RefreshTokens;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeviceSessionServiceTest {

	static final String DEVICE_A = "11111111-1111-1111-1111-111111111111";
	static final String DEVICE_B = "22222222-2222-2222-2222-222222222222";

	@Autowired
	MemberDeviceRepository repo;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	TestEntityManager em;

	MutableClock clock;
	DeviceSessionService service;

	@BeforeEach
	void setUp() {
		JwtProperties props = new JwtProperties(
			"test-issuer",
			new JwtProperties.MemberToken("member-secret-0123456789-0123456789-abc", Duration.ofMinutes(30), Duration.ofDays(60)),
			new JwtProperties.AdminToken("admin-secret-0123456789-0123456789-xyz", Duration.ofHours(2)));
		clock = new MutableClock(Instant.now());
		service = new DeviceSessionService(repo, memberRepository, new AccessTokenIssuer(props, clock), props, clock);
		// rotate 는 유효 회원인지 확인하므로 테스트에서 쓰는 회원을 미리 만들어 둔다.
		memberRepository.save(Member.create("m1"));
		memberRepository.save(Member.create("m2"));
	}

	@Test
	void openSessionIssuesTokensAndStoresOnlyHash() {
		IssuedTokens t = service.openSession("m1", DEVICE_A, "IOS", "fcm-1");

		MemberDevice d = repo.findById(DEVICE_A).orElseThrow();
		assertThat(d.getRefreshTokenHash()).isEqualTo(RefreshTokens.sha256Hex(t.refreshToken()));
		assertThat(d.getRefreshTokenHash()).isNotEqualTo(t.refreshToken());
		assertThat(d.getMemberId()).isEqualTo("m1");
		assertThat(d.getFcmToken()).isEqualTo("fcm-1");
		assertThat(t.accessTokenExpiresIn()).isEqualTo(1800);
		assertThat(t.refreshTokenExpiresIn()).isEqualTo(5_184_000);
		assertThat(t.accessToken()).isNotBlank();
	}

	@Test
	void rotateReturnsNewPairAndInvalidatesNothingYet() {
		IssuedTokens t1 = service.openSession("m1", DEVICE_A, "IOS", null);

		IssuedTokens t2 = service.rotate(t1.refreshToken(), DEVICE_A);

		assertThat(t2.refreshToken()).isNotEqualTo(t1.refreshToken());
		MemberDevice d = repo.findById(DEVICE_A).orElseThrow();
		assertThat(d.getRefreshTokenHash()).isEqualTo(RefreshTokens.sha256Hex(t2.refreshToken()));
		assertThat(d.getPrevRefreshTokenHash()).isEqualTo(RefreshTokens.sha256Hex(t1.refreshToken()));
	}

	@Test
	void rotateInvalidatesPreviousTokenAfterGrace() {
		IssuedTokens t1 = service.openSession("m1", DEVICE_A, "IOS", null);
		IssuedTokens t2 = service.rotate(t1.refreshToken(), DEVICE_A);
		clock.advance(Duration.ofSeconds(61));

		assertThatThrownBy(() -> service.rotate(t1.refreshToken(), DEVICE_A))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_REUSED);
		assertThat(repo.findById(DEVICE_A)).isEmpty();
		assertThatThrownBy(() -> service.rotate(t2.refreshToken(), DEVICE_A))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_INVALID);
	}

	@Test
	void retryWithPreviousTokenWithinGraceIssuesNewPair() {
		IssuedTokens t1 = service.openSession("m1", DEVICE_A, "IOS", null);
		service.rotate(t1.refreshToken(), DEVICE_A);
		clock.advance(Duration.ofSeconds(10));

		IssuedTokens t3 = service.rotate(t1.refreshToken(), DEVICE_A);

		assertThat(t3.refreshToken()).isNotEqualTo(t1.refreshToken());
		assertThatCode(() -> service.rotate(t3.refreshToken(), DEVICE_A)).doesNotThrowAnyException();
	}

	@Test
	void expiredRefreshDeletesSessionAndIsInvalid() {
		IssuedTokens t1 = service.openSession("m1", DEVICE_A, "IOS", null);
		clock.advance(Duration.ofDays(60).plusSeconds(1));

		assertThatThrownBy(() -> service.rotate(t1.refreshToken(), DEVICE_A))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_INVALID);
		assertThat(repo.findById(DEVICE_A)).isEmpty();
	}

	@Test
	void rotateForInvalidatedMemberDeletesSessionAndIsInvalid() {
		IssuedTokens t1 = service.openSession("m1", DEVICE_A, "IOS", null);
		memberRepository.softDeleteOne("m1", java.time.LocalDateTime.now(clock));

		assertThatThrownBy(() -> service.rotate(t1.refreshToken(), DEVICE_A))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_INVALID);
		assertThat(repo.findById(DEVICE_A)).isEmpty();
	}

	@Test
	void unknownTokenIsInvalid() {
		service.openSession("m1", DEVICE_A, "IOS", null);

		assertThatThrownBy(() -> service.rotate("garbage", DEVICE_A))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_INVALID);
		assertThat(repo.findById(DEVICE_A)).isPresent();
	}

	@Test
	void refreshOfAnotherDeviceIsRejectedWithoutTouchingIt() {
		service.openSession("m1", DEVICE_A, "IOS", null);
		IssuedTokens tokenOfB = service.openSession("m2", DEVICE_B, "AND", null);

		assertThatThrownBy(() -> service.rotate(tokenOfB.refreshToken(), DEVICE_A))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_INVALID);
		assertThat(repo.findById(DEVICE_A)).isPresent();
		assertThat(repo.findById(DEVICE_B)).isPresent();
		assertThatCode(() -> service.rotate(tokenOfB.refreshToken(), DEVICE_B)).doesNotThrowAnyException();
	}

	@Test
	void otherMemberLoginOnSameDeviceKillsPreviousRefresh() {
		IssuedTokens t1 = service.openSession("m1", DEVICE_A, "IOS", "fcm-1");
		service.rotate(t1.refreshToken(), DEVICE_A); // m1 의 직전 토큰이 유예 구간에 있는 상태

		service.openSession("m2", DEVICE_A, "IOS", null);

		assertThatThrownBy(() -> service.rotate(t1.refreshToken(), DEVICE_A))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.REFRESH_INVALID);
		MemberDevice d = repo.findById(DEVICE_A).orElseThrow();
		assertThat(d.getMemberId()).isEqualTo("m2");
		assertThat(d.getFcmToken()).isNull();
		assertThat(d.getPrevRefreshTokenHash()).isNull();
	}

	@Test
	void fcmTokenMovesToNewDevice() {
		service.openSession("m1", DEVICE_A, "IOS", "fcm-X");

		service.openSession("m2", DEVICE_B, "AND", "fcm-X");

		assertThat(repo.findById(DEVICE_A).orElseThrow().getFcmToken()).isNull();
		assertThat(repo.findById(DEVICE_B).orElseThrow().getFcmToken()).isEqualTo("fcm-X");
	}

	@Test
	void logoutIsIdempotent() {
		service.openSession("m1", DEVICE_A, "IOS", null);

		service.logout("m1", DEVICE_A);
		assertThat(repo.findById(DEVICE_A)).isEmpty();
		assertThatCode(() -> service.logout("m1", DEVICE_A)).doesNotThrowAnyException();
		assertThatCode(() -> service.logout("m1", DEVICE_B)).doesNotThrowAnyException();
	}

	@Test
	void logoutOfForeignDeviceDoesNothing() {
		service.openSession("m1", DEVICE_A, "IOS", null);

		service.logout("m2", DEVICE_A);

		assertThat(repo.findById(DEVICE_A)).isPresent();
	}

	@Test
	void updateFcmTokenOnForeignDeviceIsNotFound() {
		service.openSession("m1", DEVICE_A, "IOS", "fcm-1");

		assertThatThrownBy(() -> service.updateFcmToken("m2", DEVICE_A, "fcm-2"))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.DEVICE_NOT_FOUND);
		assertThat(repo.findById(DEVICE_A).orElseThrow().getFcmToken()).isEqualTo("fcm-1");
	}

	@Test
	void updateFcmTokenDetachesSameTokenFromOtherDevice() {
		service.openSession("m1", DEVICE_A, "IOS", "fcm-X");
		service.openSession("m2", DEVICE_B, "AND", "fcm-Y");

		service.updateFcmToken("m2", DEVICE_B, "fcm-X");

		assertThat(repo.findById(DEVICE_A).orElseThrow().getFcmToken()).isNull();
		assertThat(repo.findById(DEVICE_B).orElseThrow().getFcmToken()).isEqualTo("fcm-X");
	}

	@Test
	void deleteAllOfRemovesEveryDeviceOfMember() {
		service.openSession("m1", DEVICE_A, "IOS", null);
		service.openSession("m1", DEVICE_B, "AND", null);
		service.openSession("m2", "33333333-3333-3333-3333-333333333333", "AND", null);

		service.deleteAllOf("m1");

		assertThat(repo.findAll()).extracting(MemberDevice::getMemberId).containsExactly("m2");
	}

	/** 테스트용 가변 시계. */
	static class MutableClock extends Clock {
		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration d) {
			instant = instant.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
