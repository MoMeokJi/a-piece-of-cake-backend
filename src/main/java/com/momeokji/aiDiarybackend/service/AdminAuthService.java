package com.momeokji.aiDiarybackend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.common.util.JwtUtil;
import com.momeokji.aiDiarybackend.dto.request.AdminSignupRequestDto;
import com.momeokji.aiDiarybackend.dto.response.AdminLoginResultDto;
import com.momeokji.aiDiarybackend.dto.response.AdminResponseDto;
import com.momeokji.aiDiarybackend.entity.Admin;
import com.momeokji.aiDiarybackend.repository.AdminRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

	private final AdminRepository adminRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtUtil jwt;

	@Transactional
	public void signup(AdminSignupRequestDto req) {
		if (adminRepository.findByAdminId(req.getAdminId()).isPresent()) {
			throw new IllegalArgumentException("이미 가입된 관리자 계정입니다.");
		}

		adminRepository.save(
			Admin.builder()
				.adminId(req.getAdminId())
				.adminPwd(passwordEncoder.encode(req.getAdminPwd()))
				.createdAt(LocalDateTime.now())
				.isValid(false)
				.isSuper(false)
				.build()
		);
	}

	@Transactional(readOnly = true)
	public AdminLoginResultDto login(AdminSignupRequestDto req) {
		Admin admin = adminRepository.findByAdminId(req.getAdminId())
			.orElseThrow(() -> new IllegalArgumentException("관리자 계정을 찾을 수 없습니다."));

		if (!Boolean.TRUE.equals(admin.getIsValid())) {
			throw new IllegalArgumentException("master 관리자 승인 대기 중인 계정입니다.");
		}

		if (!passwordEncoder.matches(req.getAdminPwd(), admin.getAdminPwd())) {
			throw new IllegalArgumentException("관리자 비밀번호가 일치하지 않습니다.");
		}

		return AdminLoginResultDto.builder()
			.accessToken(jwt.generateAdminAccessToken(admin))
			.refreshToken(jwt.generateAdminRefreshToken(admin))
			.isSuper(admin.getIsSuper())
			.build();
	}

	@Transactional
	public void approveAdmin(String superAdminId, String targetAdminId) {
		validateSuperAdmin(superAdminId);

		Admin targetAdmin = adminRepository.findByAdminId(targetAdminId)
			.orElseThrow(() -> new IllegalArgumentException("승인할 관리자 계정을 찾을 수 없습니다."));

		if (Boolean.TRUE.equals(targetAdmin.getIsSuper())) {
			throw new IllegalArgumentException("슈퍼관리자 계정은 승인 대상이 아닙니다.");
		}

		targetAdmin.approve();
	}

	@Transactional(readOnly = true)
	public List<AdminResponseDto> getAdmins(String superAdminId) {
		validateSuperAdmin(superAdminId);

		return adminRepository.findByIsSuperFalseAndDeletedAtIsNull().stream()
			.map(AdminResponseDto::from)
			.toList();
	}

	@Transactional
	public void deleteAdmin(String superAdminId, String targetAdminId) {
		validateSuperAdmin(superAdminId);

		Admin targetAdmin = adminRepository.findByAdminId(targetAdminId)
			.orElseThrow(() -> new IllegalArgumentException("삭제할 관리자 계정을 찾을 수 없습니다."));

		if (Boolean.TRUE.equals(targetAdmin.getIsSuper())) {
			throw new IllegalArgumentException("슈퍼관리자 계정은 삭제할 수 없습니다.");
		}

		targetAdmin.delete(LocalDateTime.now());
	}

	private void validateSuperAdmin(String adminId) {
		Admin admin = adminRepository.findByAdminIdAndIsValidTrue(adminId)
			.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 관리자 계정입니다."));

		if (!Boolean.TRUE.equals(admin.getIsSuper())) {
			throw new IllegalArgumentException("슈퍼관리자만 접근할 수 있습니다.");
		}
	}
}