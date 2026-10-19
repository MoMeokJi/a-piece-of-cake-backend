package com.momeokji.aiDiarybackend.service.auth.social;

import com.momeokji.aiDiarybackend.entity.SocialProvider;

/** 검증된 소셜 ID 토큰에서 꺼낸 신원. 계정 식별 기준은 {@code (provider, providerUserId)} 다. */
public record SocialIdentity(SocialProvider provider, String providerUserId, String email) {
}
