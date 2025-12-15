package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicInsert
@Table(name = "member")
public class Member {

	@Id
	@Column(name = "member_id")
	private String memberId;

	@Column(name = "device_id")
	private String deviceId;

	@Column(name ="preference")
	private String preference;

	@Column(name = "mobile_os")
	private String mobileOS;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "is_valid", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
	@ColumnDefault("1")
	private Boolean isValid;


	@Builder
	public Member(String deviceId, String mobileOS, String preference, String memberId) {
		this.deviceId = deviceId;
		this.mobileOS = mobileOS;
		this.preference = preference;
		this.memberId = memberId;
	}

}
