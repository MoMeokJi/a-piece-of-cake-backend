package com.momeokji.aiDiarybackend.entity;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기 한 대 = 로그인 세션 하나(스펙 3.3, 3.5).
 *
 * <p>refresh 토큰은 원문을 저장하지 않고 SHA-256(hex) 만 둔다. 직전 해시는 네트워크 재시도와
 * 재사용(탈취) 감지를 구분하기 위해 {@code rotatedAt} 과 함께 보관한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
	name = "member_device",
	indexes = {
		@Index(name = "idx_member_device_member", columnList = "member_id"),
		@Index(name = "idx_member_device_prev_hash", columnList = "prev_refresh_token_hash")
	}
)
public class MemberDevice {

	/** 앱이 처음 실행할 때 만든 UUID. 이름표일 뿐 인증 수단이 아니다. */
	@Id
	@Column(name = "device_id", length = 36)
	private String deviceId;

	@Column(name = "member_id", nullable = false)
	private String memberId;

	/** AND, IOS */
	@Column(name = "os", nullable = false, length = 10)
	private String os;

	@Column(name = "fcm_token", length = 512, unique = true)
	private String fcmToken;

	@Column(name = "refresh_token_hash", nullable = false, length = 64, unique = true)
	private String refreshTokenHash;

	@Column(name = "prev_refresh_token_hash", length = 64)
	private String prevRefreshTokenHash;

	@Column(name = "rotated_at")
	private LocalDateTime rotatedAt;

	@Column(name = "refresh_expires_at", nullable = false)
	private LocalDateTime refreshExpiresAt;

	/** 마지막 로그인 또는 refresh 시각. 요청마다 갱신하지 않는다. */
	@Column(name = "last_used_at", nullable = false)
	private LocalDateTime lastUsedAt;

	@Column(name = "created_at", updatable = false, nullable = false)
	private LocalDateTime createdAt;

	public static MemberDevice open(String deviceId, String memberId, String os, String fcmToken,
		String refreshTokenHash, LocalDateTime refreshExpiresAt, LocalDateTime now) {
		MemberDevice device = new MemberDevice();
		device.deviceId = deviceId;
		device.memberId = memberId;
		device.os = os;
		device.fcmToken = fcmToken;
		device.refreshTokenHash = refreshTokenHash;
		device.refreshExpiresAt = refreshExpiresAt;
		device.lastUsedAt = now;
		device.createdAt = now;
		return device;
	}

	/**
	 * 같은 기기에 다시 로그인했을 때 행을 덮어쓴다.
	 *
	 * <p>다른 회원이면 전달값(NULL 포함)으로 전부 교체한다. 같은 회원이 fcmToken 없이 다시 로그인하면
	 * 기존 FCM 토큰을 유지한다. 직전 해시와 회전 시각은 항상 비운다 — 이전 세션의 직전 토큰이 유예
	 * 구간을 타고 통과하면 안 되기 때문이다.
	 */
	public void reassign(String memberId, String os, String fcmToken, String refreshTokenHash,
		LocalDateTime refreshExpiresAt, LocalDateTime now) {
		boolean sameMember = this.memberId.equals(memberId);
		this.memberId = memberId;
		this.os = os;
		if (!(sameMember && fcmToken == null)) {
			this.fcmToken = fcmToken;
		}
		this.refreshTokenHash = refreshTokenHash;
		this.prevRefreshTokenHash = null;
		this.rotatedAt = null;
		this.refreshExpiresAt = refreshExpiresAt;
		this.lastUsedAt = now;
	}

	/** refresh 회전: 현재 해시를 직전으로 옮기고 새 해시를 현재로 둔다. */
	public void rotate(String newRefreshTokenHash, LocalDateTime refreshExpiresAt, LocalDateTime now) {
		this.prevRefreshTokenHash = this.refreshTokenHash;
		this.refreshTokenHash = newRefreshTokenHash;
		this.rotatedAt = now;
		this.refreshExpiresAt = refreshExpiresAt;
		this.lastUsedAt = now;
	}

	public void changeFcmToken(String fcmToken) {
		this.fcmToken = fcmToken;
	}

	public boolean isRefreshExpired(LocalDateTime now) {
		return !now.isBefore(refreshExpiresAt);
	}

	/** 직전 토큰으로 온 요청이 네트워크 재시도로 볼 수 있는 유예 구간 안인지. */
	public boolean isWithinRetryGrace(LocalDateTime now, Duration grace) {
		return rotatedAt != null && !now.isAfter(rotatedAt.plus(grace));
	}
}
