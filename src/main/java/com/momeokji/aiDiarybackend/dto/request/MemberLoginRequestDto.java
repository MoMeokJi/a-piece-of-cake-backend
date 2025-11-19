package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class MemberLoginRequestDto {

	@NotBlank(message = "deviceId는 필수입니다.")
	private String deviceId;
}
