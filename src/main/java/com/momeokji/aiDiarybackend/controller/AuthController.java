package com.momeokji.aiDiarybackend.controller;

import com.momeokji.aiDiarybackend.dto.request.MemberSignupRequestDto;
import com.momeokji.aiDiarybackend.dto.response.TokenResponseDto;
import com.momeokji.aiDiarybackend.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	// 토큰 발급(가입/로그인 같이 쓸 듯)
	@PostMapping("/users")
	public ResponseEntity<TokenResponseDto> issue(@Validated @RequestBody MemberSignupRequestDto req) {
		return ResponseEntity.ok(authService.issueTokens(req));
	}

	// 리프레시
	@PostMapping("/auth/refresh")
	public ResponseEntity<TokenResponseDto> refresh(@RequestParam String refreshToken) {
		return ResponseEntity.ok(authService.refresh(refreshToken));
	}

	//userId체크
	@GetMapping("/me")
	public ResponseEntity<String> me(org.springframework.security.core.Authentication auth) {
		return ResponseEntity.ok(auth.getName()); // JwtAuthFilter에서 principal=userId
	}
}
