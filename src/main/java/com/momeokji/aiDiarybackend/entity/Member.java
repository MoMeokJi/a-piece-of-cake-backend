package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원(스펙 3.1). 로그인 수단은 {@link SocialAccount}, 기기·푸시 주소·세션은 {@link MemberDevice} 에 있다.
 *
 * <p>DB 의 {@code device_id, mobile_os, preference, last_active_at} 컬럼은 남아 있지만 더 이상 매핑하지 않는다.
 * (ddl-auto=validate 는 엔티티 필드가 DB 에 있는지만 본다.) 컬럼 삭제는 추후 별도 SQL 로 한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicInsert
@Table(name = "member")
public class Member {

	/** 외부에 노출되는 유일한 회원 식별자(UUID). 서버가 처음 보는 소셜 계정으로 로그인할 때 만든다. */
	@Id
	@Column(name = "member_id")
	private String memberId;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false, nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "is_valid", nullable = false, columnDefinition = "TINYINT")
	@ColumnDefault("1")
	private Boolean isValid;

	public static Member create(String memberId) {
		Member member = new Member();
		member.memberId = memberId;
		member.isValid = true;
		return member;
	}
}
