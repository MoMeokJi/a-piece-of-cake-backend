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
import com.momeokji.aiDiarybackend.dto.request.NotificationUpdateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DiaryStatsResponseDto;
import com.momeokji.aiDiarybackend.dto.response.NicknameResponseDto;
import com.momeokji.aiDiarybackend.dto.response.NotificationResponseDto;
import com.momeokji.aiDiarybackend.dto.response.UserMeResponseDto;
import com.momeokji.aiDiarybackend.service.AuthService;
import com.momeokji.aiDiarybackend.service.DiaryStatsService;
import com.momeokji.aiDiarybackend.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
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
		return ResponseEntity.ok(userService.getMe(auth.getName()));
	}

	/** 기록 알림 수신 on/off. 리마인드와 공감 알림을 함께 제어한다. */
	@Operation(summary = "기록 알림 수신 on/off", description = """
		리마인드 알림과 공감(시식평) 알림을 토글 하나로 함께 제어한다. 마이페이지 토글이 쓴다.
		가입 시점의 값은 약관 시트의 "기록 알림 받기" 동의 여부로 서버가 정하므로(로그인 요청의 `agreedTermsIds`),
		가입 직후 이 API 를 따로 부를 필요가 없다. 관리자 전체 공지는 이 값과 무관하다.
		""")
	@PutMapping("/me/notification")
	public ResponseEntity<NotificationResponseDto> changeNotification(@Validated @RequestBody NotificationUpdateRequestDto req,
		Authentication auth) {
		boolean enabled = userService.changeNotificationEnabled(auth.getName(), req.getEnabled()).getNotificationEnabled();
		return ResponseEntity.ok(new NotificationResponseDto(enabled));
	}

	/** 마이페이지 통계. 모두 KST 기준. */
	@Operation(summary = "마이페이지 통계", description = """
		모두 KST 기준이며 삭제된 일기는 세지 않는다.
		`streakDays` 는 오늘 기록이 있으면 오늘부터, 없으면 어제부터 거슬러 올라가며 센 연속 일수다.
		`todayCount` 가 `dailyLimit` 에 도달하면 생성 API 는 429 `DIARY_DAILY_LIMIT_EXCEEDED` 를 돌려준다.
		""")
	@GetMapping("/me/stats")
	public ResponseEntity<DiaryStatsResponseDto> stats(Authentication auth) {
		return ResponseEntity.ok(diaryStatsService.getStats(auth.getName()));
	}

	/** 닉네임 설정·수정. 한글/영문/숫자 2~10자, 그 외는 400 VALIDATION_FAILED. */
	@Operation(summary = "닉네임 설정·수정", description = """
		규칙: 정규식 `^[가-힣A-Za-z0-9]{2,10}$` — 완성형 한글·영문·숫자 2~10자. 공백, 자모(ㄱ, ㅏ), 이모지, 특수문자는 불가.
		중복은 허용한다(다른 회원과 같은 닉네임 가능). 실시간 중복 검사 API 는 없으며 앱이 같은 정규식으로 로컬 검사한다.
		위반 시 400 `VALIDATION_FAILED`.
		""")
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
