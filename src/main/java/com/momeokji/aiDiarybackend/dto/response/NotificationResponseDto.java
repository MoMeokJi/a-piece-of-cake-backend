package com.momeokji.aiDiarybackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** {@code PUT /users/me/notification} 응답. */
@Getter
@AllArgsConstructor
public class NotificationResponseDto {
	private boolean notificationEnabled;
}
