package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 가입할 때 동의한 약관 한 건(스펙 2026-11-27 4.1). {@code termsId} 가 약관의 버전까지 가리킨다.
 * 탈퇴하면 지운다 — 개인정보처리방침 4항이 탈퇴 후에는 회원 번호와 가입·탈퇴 일시만 남는다고 약속한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
	name = "member_terms_agreement",
	// 회원별 조회·삭제도 이 유니크 키의 앞 컬럼(member_id)을 탄다. 별도 인덱스는 두지 않는다.
	uniqueConstraints = @UniqueConstraint(name = "uk_member_terms", columnNames = {"member_id", "terms_id"})
)
public class MemberTermsAgreement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "member_id", nullable = false)
	private String memberId;

	@Column(name = "terms_id", nullable = false)
	private Long termsId;

	/** 서버가 가입을 처리한 시각. 앱이 보낸 값이 아니다. */
	@Column(name = "agreed_at", nullable = false)
	private LocalDateTime agreedAt;

	public static MemberTermsAgreement of(String memberId, Long termsId, LocalDateTime agreedAt) {
		MemberTermsAgreement agreement = new MemberTermsAgreement();
		agreement.memberId = memberId;
		agreement.termsId = termsId;
		agreement.agreedAt = agreedAt;
		return agreement;
	}
}
