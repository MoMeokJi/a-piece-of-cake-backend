package com.momeokji.aiDiarybackend.entity;

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
@Table(name = "member")
public class Member {

	@Id
	@Column(name = "member_id")
	private String memberId;

	@Column(name = "device_id")
	private String deviceId;

	@Column(name ="preference")
	private String preference;

	@Column(name = "os_type")
	private String osType;

	@Builder
	public Member(String deviceId, String osType, String preference, String memberId) {
		this.deviceId = deviceId;
		this.osType = osType;
		this.preference = preference;
		this.memberId = memberId;
	}


}
