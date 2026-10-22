package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.request.AppleLoginRequestDto;
import com.momeokji.aiDiarybackend.dto.request.FcmTokenUpdateRequestDto;
import com.momeokji.aiDiarybackend.dto.request.GoogleLoginRequestDto;
import com.momeokji.aiDiarybackend.dto.request.LogoutRequestDto;
import com.momeokji.aiDiarybackend.dto.request.RefreshTokenRequestDto;
import com.momeokji.aiDiarybackend.dto.response.AuthTokenResponseDto;
import com.momeokji.aiDiarybackend.service.auth.DeviceSessionService;
import com.momeokji.aiDiarybackend.service.auth.SocialLoginService;

import lombok.RequiredArgsConstructor;

/**
 * 앱 인증 API(스펙 6.1, 6.2). 토큰은 응답 바디로만 전달한다.
 * 인증이 필요한 요청의 {@code auth.getName()} 은 member_id 다.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

	private final SocialLoginService socialLoginService;
	private final DeviceSessionService deviceSessionService;

	/** 구글 소셜 가입/로그인 (공개) */
	@PostMapping("/auth/social/google")
	public ResponseEntity<AuthTokenResponseDto> loginWithGoogle(@Validated @RequestBody GoogleLoginRequestDto req) {
		return ResponseEntity.ok(AuthTokenResponseDto.from(socialLoginService.loginWithGoogle(req)));
	}

	/** 애플 소셜 가입/로그인 (공개) */
	@PostMapping("/auth/social/apple")
	public ResponseEntity<AuthTokenResponseDto> loginWithApple(@Validated @RequestBody AppleLoginRequestDto req) {
		return ResponseEntity.ok(AuthTokenResponseDto.from(socialLoginService.loginWithApple(req)));
	}

	/** refresh 회전 (공개). 자동 로그인은 이 API 로 한다. */
	@PostMapping("/auth/refresh")
	public ResponseEntity<AuthTokenResponseDto> refresh(@Validated @RequestBody RefreshTokenRequestDto req) {
		return ResponseEntity.ok(AuthTokenResponseDto.from(deviceSessionService.rotate(req.getRefreshToken(), req.getDeviceId())));
	}

	/** 이 기기의 세션을 끊는다. 행이 없어도 204(멱등). */
	@PostMapping("/auth/logout")
	public ResponseEntity<Void> logout(@Validated @RequestBody LogoutRequestDto req, Authentication auth) {
		deviceSessionService.logout(auth.getName(), req.getDeviceId());
		return ResponseEntity.noContent().build();
	}

	/** FCM 토큰 갱신 콜백용. 내 기기가 아니면 404 DEVICE_NOT_FOUND. */
	@PutMapping("/auth/devices/fcm-token")
	public ResponseEntity<Void> updateFcmToken(@Validated @RequestBody FcmTokenUpdateRequestDto req, Authentication auth) {
		deviceSessionService.updateFcmToken(auth.getName(), req.getDeviceId(), req.getFcmToken());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/health")
	public ResponseEntity<String> health() {
		return ResponseEntity.ok("OK");
	}
}
