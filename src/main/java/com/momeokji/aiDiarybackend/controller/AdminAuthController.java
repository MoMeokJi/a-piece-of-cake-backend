package com.momeokji.aiDiarybackend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.request.AdminApproveRequestDto;
import com.momeokji.aiDiarybackend.dto.request.AdminSignupRequestDto;
import com.momeokji.aiDiarybackend.dto.response.AdminLoginResponseDto;
import com.momeokji.aiDiarybackend.dto.response.AdminResponseDto;
import com.momeokji.aiDiarybackend.service.AdminAuthService;

import lombok.RequiredArgsConstructor;

/** 관리자 인증·계정 관리(스펙 6.3). 전부 {@code /admin} 아래라 관리자 체인이 자동으로 보호한다. */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

	private final AdminAuthService adminAuthService;

	@PostMapping("/auth/signup")
	public ResponseEntity<Void> signup(@Validated @RequestBody AdminSignupRequestDto req) {
		adminAuthService.signup(req);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/auth/login")
	public ResponseEntity<AdminLoginResponseDto> login(@Validated @RequestBody AdminSignupRequestDto req) {
		return ResponseEntity.ok(adminAuthService.login(req));
	}

	/** token_version 을 올려 지금까지 발급된 이 관리자의 토큰을 전부 무효화한다. */
	@PostMapping("/auth/logout")
	public ResponseEntity<Void> logout(Authentication auth) {
		adminAuthService.logout(auth.getName());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/admins/approve")
	public ResponseEntity<Void> approveAdmin(
		@Validated @RequestBody AdminApproveRequestDto req,
		Authentication auth
	) {
		adminAuthService.approveAdmin(auth.getName(), req.getAdminId());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/admins")
	public ResponseEntity<List<AdminResponseDto>> getAdmins(Authentication auth) {
		return ResponseEntity.ok(adminAuthService.getAdmins(auth.getName()));
	}

	@DeleteMapping("/admins")
	public ResponseEntity<Void> deleteAdmin(
		@Validated @RequestBody AdminApproveRequestDto req,
		Authentication auth
	) {
		adminAuthService.deleteAdmin(auth.getName(), req.getAdminId());
		return ResponseEntity.noContent().build();
	}
}
