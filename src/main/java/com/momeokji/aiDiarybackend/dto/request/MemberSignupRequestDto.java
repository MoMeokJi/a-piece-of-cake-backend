package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class MemberSignupRequestDto {

	@NotBlank
	private String deviceId;  // FCM 토큰

	private String osType;    // "AND"/"IOS"

	private String preference;
}
