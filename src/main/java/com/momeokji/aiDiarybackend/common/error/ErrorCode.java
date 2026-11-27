package com.momeokji.aiDiarybackend.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 에러 코드. 클라이언트는 응답의 {@code code} 필드로만 분기한다(스펙 9.2).
 */
public enum ErrorCode {

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "access token이 만료되었습니다."),
	TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
	REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "refresh token이 유효하지 않습니다."),
	REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "refresh token 재사용이 감지되어 세션이 종료되었습니다."),
	SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "소셜 토큰 검증에 실패했습니다."),
	ADMIN_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	ADMIN_NOT_APPROVED(HttpStatus.FORBIDDEN, "슈퍼관리자 승인 대기 중인 계정입니다."),
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	TERMS_AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "필수 약관에 동의해야 가입할 수 있습니다."),
	DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "기기를 찾을 수 없습니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	DIARY_NOT_FOUND(HttpStatus.NOT_FOUND, "일기를 찾을 수 없습니다."),
	DIARY_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 작성할 수 있는 일기 개수를 넘었습니다."),
	TERMS_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "약관 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요."),
	SOCIAL_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "소셜 로그인 공급자에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String defaultDetail;

	ErrorCode(HttpStatus status, String defaultDetail) {
		this.status = status;
		this.defaultDetail = defaultDetail;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultDetail() {
		return defaultDetail;
	}
}
