package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code PUT /users/me/notification} 요청. 약관의 "[선택] 기록 알림 받기" 와 마이페이지 토글이 같이 쓴다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class NotificationUpdateRequestDto {

	@NotNull(message = "enabled는 필수입니다.")
	private Boolean enabled;
}
