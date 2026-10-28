package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.request.NicknameUpdateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryStatsResponseDto;
import com.momeokji.aiDiarybackend.dto.response.NicknameResponseDto;
import com.momeokji.aiDiarybackend.dto.response.UserMeResponseDto;
import com.momeokji.aiDiarybackend.service.AuthService;
import com.momeokji.aiDiarybackend.service.DiaryStatsService;
import com.momeokji.aiDiarybackend.service.UserService;

import lombok.RequiredArgsConstructor;

/** 회원 본인 리소스. {@code /users/me} 는 토큰의 회원이다. {@code auth.getName()} 은 member_id 다. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final AuthService authService;
	private final DiaryStatsService diaryStatsService;

	/** 내 정보. nickname 이 null 이면 아직 닉네임을 정하지 않은 회원이다. */
	@GetMapping("/me")
	public ResponseEntity<UserMeResponseDto> me(Authentication auth) {
		return ResponseEntity.ok(UserMeResponseDto.from(userService.getMe(auth.getName())));
	}

	/** 마이페이지 통계. 모두 KST 기준. */
	@GetMapping("/me/stats")
	public ResponseEntity<DiaryStatsResponseDto> stats(Authentication auth) {
		return ResponseEntity.ok(diaryStatsService.getStats(auth.getName()));
	}

	/** 닉네임 설정·수정. 한글/영문/숫자 2~10자, 그 외는 400 VALIDATION_FAILED. */
	@PutMapping("/me/nickname")
	public ResponseEntity<NicknameResponseDto> changeNickname(@Validated @RequestBody NicknameUpdateRequestDto req,
		Authentication auth) {
		return ResponseEntity.ok(new NicknameResponseDto(userService.changeNickname(auth.getName(), req.getNickname()).getNickname()));
	}

	/** 탈퇴 */
	@DeleteMapping
	public ResponseEntity<Void> withdraw(Authentication auth) {
		authService.withdraw(auth);
		return ResponseEntity.noContent().build();
	}
}
