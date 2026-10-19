package com.momeokji.aiDiarybackend.service.auth;

/** 소셜 로그인 결과. {@code newMember} 는 이번 로그인으로 회원이 새로 만들어졌는지다. */
public record SocialLoginResult(IssuedTokens tokens, boolean newMember) {
}
