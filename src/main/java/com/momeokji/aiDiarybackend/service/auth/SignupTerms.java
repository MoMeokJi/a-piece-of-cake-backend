package com.momeokji.aiDiarybackend.service.auth;

import java.util.List;

/**
 * 신규 가입 요청이 동의한 약관 중 지금 유효한 것. {@link com.momeokji.aiDiarybackend.service.TermsService#resolveForSignup}
 * 이 필수 동의를 검증한 뒤에만 만들어진다.
 *
 * @param agreedTermsIds    동의 기록으로 남길 약관 ID(활성 약관만, 시트 순서)
 * @param notificationOptIn "기록 알림 받기"에 동의했는지. 가입 시점의 알림 설정이 된다.
 */
public record SignupTerms(List<Long> agreedTermsIds, boolean notificationOptIn) {
}
