package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code PUT /users/me/notification} 요청. 마이페이지 토글이 쓴다. 가입 시점의 값은 약관 동의로 정해진다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NotificationUpdateRequestDto {

	@NotNull(message = "enabled는 필수입니다.")
	private Boolean enabled;
}
