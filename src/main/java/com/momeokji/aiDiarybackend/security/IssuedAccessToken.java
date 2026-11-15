package com.momeokji.aiDiarybackend.security;

/** 발급된 access 토큰과 남은 유효 시간(초). */
public record IssuedAccessToken(String value, long expiresInSeconds) {
}
