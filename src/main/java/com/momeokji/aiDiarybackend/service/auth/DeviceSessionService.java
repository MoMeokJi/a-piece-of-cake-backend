package com.momeokji.aiDiarybackend.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.config.JwtProperties;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.entity.MemberDevice;
import com.momeokji.aiDiarybackend.repository.MemberDeviceRepository;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.security.AccessTokenIssuer;
import com.momeokji.aiDiarybackend.security.IssuedAccessToken;
import com.momeokji.aiDiarybackend.security.RefreshTokens;

import lombok.extern.slf4j.Slf4j;

/**
 * 기기 세션(= refresh 토큰) 관리(스펙 3.5, 4.2, 4.3).
 *
 * <p>기기 한 대에 로그인한 회원은 한 명뿐이다. refresh 는 사용할 때마다 회전하고, 직전 토큰은 60초 유예
 * 안에서만 네트워크 재시도로 인정한다. 유예를 넘긴 직전 토큰은 탈취로 보고 세션을 끊는다.
 */
@Slf4j
@Service
public class DeviceSessionService {

	public static final Duration REFRESH_RETRY_GRACE = Duration.ofSeconds(60);

	private final MemberDeviceRepository memberDeviceRepository;
	private final MemberRepository memberRepository;
	private final AccessTokenIssuer accessTokenIssuer;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	public DeviceSessionService(MemberDeviceRepository memberDeviceRepository, MemberRepository memberRepository,
		AccessTokenIssuer accessTokenIssuer, JwtProperties jwtProperties, Clock clock) {
		this.memberDeviceRepository = memberDeviceRepository;
		this.memberRepository = memberRepository;
		this.accessTokenIssuer = accessTokenIssuer;
		this.jwtProperties = jwtProperties;
		this.clock = clock;
	}

	/** 로그인: 기기 행을 만들거나(없으면) 덮어쓰고(있으면) 토큰 한 쌍을 발급한다. */
	@Transactional
	public IssuedTokens openSession(String memberId, String deviceId, String os, String fcmToken) {
		if (fcmToken != null) {
			memberDeviceRepository.detachFcmTokenFromOthers(fcmToken, deviceId);
		}
		LocalDateTime now = now();
		String refreshToken = RefreshTokens.generate();
		String hash = RefreshTokens.sha256Hex(refreshToken);
		LocalDateTime expiresAt = now.plus(refreshTtl());

		Optional<MemberDevice> existing = memberDeviceRepository.findById(deviceId);
		if (existing.isPresent()) {
			existing.get().reassign(memberId, os, fcmToken, hash, expiresAt, now);
		} else {
			memberDeviceRepository.save(MemberDevice.open(deviceId, memberId, os, fcmToken, hash, expiresAt, now));
		}
		// 응답에 실을 닉네임. 로그인 직후 호출되므로 회원 행은 이미 있다.
		String nickname = memberRepository.findById(memberId).map(Member::getNickname).orElse(null);
		return issue(memberId, refreshToken, nickname);
	}

	/**
	 * refresh 회전(스펙 4.3). 기기 행을 잠근 뒤 현재/직전 해시와 비교한다.
	 * 만료·재사용으로 행을 지운 다음 예외를 던지므로, 그 삭제가 롤백되지 않도록 noRollbackFor 를 둔다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public IssuedTokens rotate(String refreshToken, String deviceId) {
		String hash = RefreshTokens.sha256Hex(refreshToken);
		LocalDateTime now = now();

		MemberDevice device = memberDeviceRepository.findWithLockByDeviceId(deviceId)
			.orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_INVALID));

		// 탈퇴(is_valid=0)한 회원의 세션이 남아 있으면 지우고 끊는다. 새 access 토큰을 내주면 안 된다.
		Member member = memberRepository.findByMemberIdAndIsValidTrue(device.getMemberId()).orElse(null);
		if (member == null) {
			memberDeviceRepository.delete(device);
			throw new BusinessException(ErrorCode.REFRESH_INVALID);
		}

		if (hash.equals(device.getRefreshTokenHash())) {
			if (device.isRefreshExpired(now)) {
				memberDeviceRepository.delete(device);
				throw new BusinessException(ErrorCode.REFRESH_INVALID);
			}
			return rotateAndIssue(device, member, now);
		}

		if (hash.equals(device.getPrevRefreshTokenHash())) {
			if (device.isWithinRetryGrace(now, REFRESH_RETRY_GRACE) && !device.isRefreshExpired(now)) {
				// 네트워크 재시도: 직전 토큰으로 다시 왔다. 한 번 더 회전해서 새 쌍을 준다.
				return rotateAndIssue(device, member, now);
			}
			// 유예를 넘긴 직전 토큰 → 탈취로 판단. 세션을 끊는다.
			log.warn("refresh 토큰 재사용 감지. deviceId={}, memberId={}", deviceId, device.getMemberId());
			memberDeviceRepository.delete(device);
			throw new BusinessException(ErrorCode.REFRESH_REUSED);
		}

		throw new BusinessException(ErrorCode.REFRESH_INVALID);
	}

	/** 내 소유 기기 행을 지운다. 없어도 조용히 끝난다(멱등). */
	@Transactional
	public void logout(String memberId, String deviceId) {
		memberDeviceRepository.findByDeviceIdAndMemberId(deviceId, memberId)
			.ifPresent(memberDeviceRepository::delete);
	}

	/** 내 소유 기기의 FCM 토큰을 바꾼다. 같은 토큰을 가진 다른 기기에서는 떼어낸다. */
	@Transactional
	public void updateFcmToken(String memberId, String deviceId, String fcmToken) {
		MemberDevice device = memberDeviceRepository.findByDeviceIdAndMemberId(deviceId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
		memberDeviceRepository.detachFcmTokenFromOthers(fcmToken, deviceId);
		// 벌크 UPDATE 가 영속성 컨텍스트를 비우므로 다시 읽어서 수정한다.
		memberDeviceRepository.findById(device.getDeviceId()).orElseThrow().changeFcmToken(fcmToken);
	}

	@Transactional
	public void deleteAllOf(String memberId) {
		memberDeviceRepository.deleteByMemberId(memberId);
	}

	private IssuedTokens rotateAndIssue(MemberDevice device, Member member, LocalDateTime now) {
		String refreshToken = RefreshTokens.generate();
		device.rotate(RefreshTokens.sha256Hex(refreshToken), now.plus(refreshTtl()), now);
		return issue(device.getMemberId(), refreshToken, member.getNickname());
	}

	private IssuedTokens issue(String memberId, String refreshToken, String nickname) {
		IssuedAccessToken access = accessTokenIssuer.issueForMember(memberId);
		return new IssuedTokens(access.value(), access.expiresInSeconds(), refreshToken, refreshTtl().toSeconds(), nickname);
	}

	private Duration refreshTtl() {
		return jwtProperties.member().refreshTokenTtl();
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}
}
