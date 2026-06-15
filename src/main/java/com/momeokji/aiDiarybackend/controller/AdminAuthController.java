package com.momeokji.aiDiarybackend.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.request.AdminApproveRequestDto;
import com.momeokji.aiDiarybackend.dto.request.AdminSignupRequestDto;
import com.momeokji.aiDiarybackend.dto.response.AdminLoginResponseDto;
import com.momeokji.aiDiarybackend.dto.response.AdminLoginResultDto;
import com.momeokji.aiDiarybackend.dto.response.AdminResponseDto;
import com.momeokji.aiDiarybackend.service.AdminAuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminAuthController {

	private final AdminAuthService adminAuthService;

	@PostMapping("/admins")
	public ResponseEntity<Void> signup(@Validated @RequestBody AdminSignupRequestDto req) {
		adminAuthService.signup(req);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/admins/login")
	public ResponseEntity<AdminLoginResponseDto> login(@Validated @RequestBody AdminSignupRequestDto req) {
		AdminLoginResultDto result = adminAuthService.login(req);

		return ResponseEntity.ok()
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + result.getAccessToken())
			.header("Refresh-Token", result.getRefreshToken())
			.body(AdminLoginResponseDto.builder()
				.isSuper(result.getIsSuper())
				.build());
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