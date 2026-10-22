package com.momeokji.aiDiarybackend.dto.request;

/** 요청 DTO 공용 검증 패턴. */
public final class RequestPatterns {

	public static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
	public static final String UUID_MESSAGE = "deviceId는 UUID 형식이어야 합니다.";
	public static final String OS = "^(AND|IOS)$";
	public static final String OS_MESSAGE = "os는 AND 또는 IOS 여야 합니다.";
	/** 완성형 한글·영문·숫자 2~10자. 공백, 자모(ㄱ, ㅏ), 이모지, 특수문자는 허용하지 않는다. */
	public static final String NICKNAME = "^[가-힣A-Za-z0-9]{2,10}$";
	public static final String NICKNAME_MESSAGE = "닉네임은 한글, 영문, 숫자로 2~10자여야 합니다.";

	private RequestPatterns() {
	}
}
