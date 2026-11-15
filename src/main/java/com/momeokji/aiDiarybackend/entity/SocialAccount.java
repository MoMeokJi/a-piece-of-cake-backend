package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜 계정과 회원의 연결(스펙 3.2). 계정 식별 기준은 검증된 ID 토큰의 {@code (provider, sub)} 다.
 *
 * <p>{@code id} 는 내부 전용 인조 키다. 응답, 토큰, URL 어디에도 노출하지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
	name = "social_account",
	uniqueConstraints = @UniqueConstraint(name = "uk_social_account_provider_user", columnNames = {"provider", "provider_user_id"}),
	indexes = @Index(name = "idx_social_account_member", columnList = "member_id")
)
public class SocialAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "member_id", nullable = false)
	private String memberId;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 20)
	private SocialProvider provider;

	@Column(name = "provider_user_id", nullable = false)
	private String providerUserId;

	/** 참고용. 애플은 첫 로그인 때만 주고, 가린 주소일 수 있다. */
	@Column(name = "email", length = 320)
	private String email;

	/** 애플 탈퇴 revoke 용 refresh 토큰. AES-GCM 암호문을 base64 로 저장한다. 애플이 아니면 NULL. */
	@Column(name = "apple_refresh_token", length = 1024)
	private String appleRefreshToken;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false, nullable = false)
	private LocalDateTime createdAt;

	public static SocialAccount link(String memberId, SocialProvider provider, String providerUserId, String email) {
		SocialAccount account = new SocialAccount();
		account.memberId = memberId;
		account.provider = provider;
		account.providerUserId = providerUserId;
		account.email = email;
		return account;
	}

	/** 비어 있지 않을 때만 갱신한다. 애플 재로그인에는 email 이 없으므로 기존 값을 지우지 않기 위해서다. */
	public void updateEmailIfPresent(String email) {
		if (email != null && !email.isBlank()) {
			this.email = email;
		}
	}

	public void storeAppleRefreshToken(String encrypted) {
		this.appleRefreshToken = encrypted;
	}
}
