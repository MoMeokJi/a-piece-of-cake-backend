package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class MemberSignupRequestDto {

	@NotBlank(message = "deviceId는 필수입니다.")
	private String deviceId;  // FCM 토큰

	@NotNull(message = "mobileOS는 필수입니다.")
	private String mobileOS;    // "AND"/"IOS"

	@NotNull(message = "preference는 필수입니다.")
	private String preference;
}
