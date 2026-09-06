package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FcmMessageRequestDto {

	@NotBlank(message = "알림 제목은 필수입니다.")
	@Size(max = 50, message = "알림 제목은 50자 이하로 입력해야 합니다.")
	private String title;

	@NotBlank(message = "알림 내용은 필수입니다.")
	@Size(max = 200, message = "알림 제목은 200자 이하로 입력해야 합니다.")
	private String body;
}