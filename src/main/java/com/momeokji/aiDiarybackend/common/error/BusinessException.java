package com.momeokji.aiDiarybackend.common.error;

/**
 * 비즈니스 규칙 위반. {@link GlobalExceptionHandler} 가 {@link ErrorCode} 에 맞는 Problem Details 로 바꾼다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		this(errorCode, errorCode.defaultDetail());
	}

	public BusinessException(ErrorCode errorCode, String detail) {
		super(detail);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
