package com.momeokji.aiDiarybackend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.dto.request.AdminSignupRequestDto;
import com.momeokji.aiDiarybackend.dto.response.AdminLoginResponseDto;
import com.momeokji.aiDiarybackend.dto.response.AdminResponseDto;
import com.momeokji.aiDiarybackend.entity.Admin;
import com.momeokji.aiDiarybackend.repository.AdminRepository;
import com.momeokji.aiDiarybackend.security.AccessTokenIssuer;
import com.momeokji.aiDiarybackend.security.IssuedAccessToken;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

	private final AdminRepository adminRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccessTokenIssuer accessTokenIssuer;

	/** 아이디가 없을 때도 비밀번호 비교에 비슷한 시간을 쓰기 위한 더미 해시. */
	private static final String DUMMY_HASH = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("dummy");

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

	/**
	 * 로그인(스펙 9.3). 아이디와 비밀번호를 먼저 확인하고, 둘 다 맞을 때만 승인 여부를 알린다.
	 * 아이디 오류와 비밀번호 오류의 응답은 같다 — 아이디 존재 여부를 노출하지 않기 위해서다.
	 */
	@Transactional(readOnly = true)
	public AdminLoginResponseDto login(AdminSignupRequestDto req) {
		Admin admin = adminRepository.findByAdminId(req.getAdminId()).orElse(null);
		boolean loginable = admin != null && admin.getDeletedAt() == null;
		boolean passwordMatches = passwordEncoder.matches(req.getAdminPwd(), loginable ? admin.getAdminPwd() : DUMMY_HASH);

		if (!loginable || !passwordMatches) {
			throw new BusinessException(ErrorCode.ADMIN_LOGIN_FAILED);
		}
		if (!Boolean.TRUE.equals(admin.getIsValid())) {
			throw new BusinessException(ErrorCode.ADMIN_NOT_APPROVED);
		}

		IssuedAccessToken access = accessTokenIssuer.issueForAdmin(admin);
		return AdminLoginResponseDto.builder()
			.accessToken(access.value())
			.accessTokenExpiresIn(access.expiresInSeconds())
			.isSuper(admin.getIsSuper())
			.build();
	}

	/** token_version +1 → 이 관리자에게 발급된 모든 access 토큰이 무효가 된다. */
	@Transactional
	public void logout(String adminId) {
		Admin admin = adminRepository.findByAdminIdAndIsValidTrue(adminId)
			.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 관리자 계정입니다."));
		admin.bumpTokenVersion();
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