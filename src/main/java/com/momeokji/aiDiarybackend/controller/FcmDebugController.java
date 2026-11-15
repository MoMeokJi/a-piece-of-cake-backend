package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.service.FcmService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 푸시 수신 테스트용. {@code FCM_DEBUG_ENABLED=true} 일 때만 뜨며, 회원의 알림 토글을 무시하고 바로 보낸다.
 * 실제 발송 경로(FcmService)를 그대로 타므로 메시지 구성(notification/data/APNs/Android 채널)을 기기에서 확인할 수 있다.
 */
@Tag(name = "FCM 테스트(디버그)", description = "환경변수 FCM_DEBUG_ENABLED=true 일 때만 열린다. 관리자 토큰 필요. 회원의 기록 알림 토글을 무시한다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/debug/fcm")
@ConditionalOnProperty(
	prefix = "app.debug",
	name = "fcm-enabled",
	havingValue = "true",
	matchIfMissing = false
)
public class FcmDebugController {

	private final FcmService fcmService;

	@Operation(summary = "리마인드 푸시 테스트 발송", description = """
		회원의 `fcm_token` 이 있는 모든 기기로 리마인드 푸시를 바로 보낸다(`data.type = REMIND`, `diaryId` 없음).
		스케줄러 조건(마지막 일기 1~14일 전, 알림 토글 켬)은 검사하지 않는다. 결과는 서버 로그로 확인한다.
		""")
	@PostMapping("/remind/{memberId}")
	public ResponseEntity<String> testRemind(@PathVariable String memberId) {
		fcmService.remindNotification(memberId);
		return ResponseEntity.ok("remind sent (check logs)");
	}

	@Operation(summary = "공감(시식평) 푸시 테스트 발송", description = """
		회원의 `fcm_token` 이 있는 모든 기기로 공감 푸시를 바로 보낸다(`data.type = FEEDBACK`, `data.diaryId` 는 문자열).
		`diaryId` 는 존재 여부를 검사하지 않고 그대로 실린다. 알림 토글은 무시한다. 결과는 서버 로그로 확인한다.
		""")
	@PostMapping("/feedback/{memberId}/{diaryId}")
	public ResponseEntity<String> testFeedback(
		@PathVariable String memberId,
		@PathVariable Long diaryId) {
		fcmService.feedbackNotification(memberId, diaryId);
		return ResponseEntity.ok("feedback sent (check logs)");
	}
}

