package com.momeokji.aiDiarybackend.controller;

import com.momeokji.aiDiarybackend.dto.request.MemberLoginRequestDto;
import com.momeokji.aiDiarybackend.dto.request.MemberSignupRequestDto;
import com.momeokji.aiDiarybackend.dto.response.TokenResponseDto;
import com.momeokji.aiDiarybackend.service.AuthService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	//회원가입, 로그인
	@PostMapping("/users")
	public ResponseEntity<Void> signup(@Validated @RequestBody MemberSignupRequestDto req) {
		TokenResponseDto tokens = authService.issueTokens(req);
		return ResponseEntity.noContent()
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.getAccessToken())
			.header("Refresh-Token", tokens.getRefreshToken())
			.build();
	}

	// 토큰 리프레시
	@PostMapping("/auth/refresh")
	public ResponseEntity<Void> refresh(@RequestHeader("Refresh-Token") String refreshToken) {
		TokenResponseDto tokens = authService.refresh(refreshToken);
		return ResponseEntity.noContent()
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.getAccessToken())
			.header("Refresh-Token", tokens.getRefreshToken())
			.build();
	}

	//재 로그인(리프레시 토큰 재발급)
	@PostMapping("/auth/login")
	public ResponseEntity<Void> login(@Validated @RequestBody MemberLoginRequestDto req) {
		TokenResponseDto tokens = authService.loginByDeviceId(req.getDeviceId());
		return ResponseEntity.noContent()
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.getAccessToken())
			.header("Refresh-Token", tokens.getRefreshToken())
			.build();
	}


	//userId 확인용
	@GetMapping("/me")
	public ResponseEntity<String> me(org.springframework.security.core.Authentication auth) {
		return ResponseEntity.ok(auth.getName()); // JwtAuthFilter에서 principal=userId
	}

	@DeleteMapping("/users")
	public ResponseEntity<Void> withdraw(Authentication auth) {
		authService.withdraw(auth);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/health")
	public ResponseEntity<String> helath(){
		return ResponseEntity.ok("OK");
	}
}
