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
@Table(name = "user")
public class User {

	@Id
	@Column(name = "user_id")
	private String userId;

	@Column(name = "device_id")
	private String deviceId;

	@Column(name ="preference")
	private String preference;

	@Column(name = "os_type")
	private String osType;

	@Builder
	public User(String deviceId, String osType, String preference, String userId) {
		this.deviceId = deviceId;
		this.osType = osType;
		this.preference = preference;
		this.userId = userId;
	}


}
