package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.request.FcmMessageRequestDto;
import com.momeokji.aiDiarybackend.service.FcmService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "FCM 전체 공지", description = "관리자 토큰 필요.")
@RestController
@RequiredArgsConstructor
public class FcmController {

	private final FcmService fcmService;

	@Operation(summary = "전체 공지 푸시", description = """
		`fcm_token` 이 있는 모든 기기로 보낸다(`data.type = ADMIN_MESSAGE`). 회원의 기록 알림 토글과 무관하게 전부 받는다.
		제목 50자, 내용 200자 이하. 기기별 실패는 건너뛰고 집계만 로그로 남기며 응답은 항상 200 이다.
		""")
	@PostMapping("/admin/fcm-messages")
	public ResponseEntity<Void> sendFcmMessage(
		@Valid @RequestBody FcmMessageRequestDto request
	) {
		fcmService.sendNotificationToAllMembers(
			request.getTitle(),
			request.getBody()
		);

		return ResponseEntity.ok().build();
	}
}