package com.momeokji.aiDiarybackend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.common.error.BusinessException;
import com.momeokji.aiDiarybackend.common.error.ErrorCode;
import com.momeokji.aiDiarybackend.entity.Terms;
import com.momeokji.aiDiarybackend.repository.TermsRepository;
import com.momeokji.aiDiarybackend.service.auth.SignupTerms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 가입 화면 약관 목록과 가입 시 필수 동의 검증(스펙 2026-11-27 4.2, 4.3). */
@Slf4j
@Service
@RequiredArgsConstructor
public class TermsService {

	private final TermsRepository termsRepository;

	@Transactional(readOnly = true)
	public List<Terms> activeTerms() {
		return termsRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
	}

	/**
	 * 신규 가입 요청의 동의 목록을 검증한다. 활성 필수 약관이 모두 들어 있어야 한다.
	 *
	 * <p>활성 약관이 아닌 ID(모르는 ID, 꺼진 버전), null, 중복은 버린다. 앱이 목록을 받은 뒤 약관이 바뀌었을 수 있기 때문이다.
	 * 그 경우 새 필수 약관이 빠져 있으면 거절되고, 앱은 목록을 다시 받는다.
	 *
	 * @throws BusinessException {@code TERMS_NOT_CONFIGURED} 활성 필수 약관이 하나도 없다(초기 데이터 누락 등).
	 *                           검증이 빈 조건으로 통과해 동의 없는 회원이 생기는 것을 막는다.
	 * @throws BusinessException {@code TERMS_AGREEMENT_REQUIRED} 필수 약관이 하나라도 빠졌다
	 */
	@Transactional(readOnly = true)
	public SignupTerms resolveForSignup(List<Long> agreedTermsIds) {
		List<Terms> active = activeTerms();
		List<Terms> required = active.stream().filter(t -> Boolean.TRUE.equals(t.getRequired())).toList();
		if (required.isEmpty()) {
			log.error("활성 필수 약관이 없어 가입을 거절합니다. terms 테이블의 초기 데이터를 확인하세요.");
			throw new BusinessException(ErrorCode.TERMS_NOT_CONFIGURED);
		}

		Set<Long> agreed = new HashSet<>();
		if (agreedTermsIds != null) {
			agreedTermsIds.stream().filter(Objects::nonNull).forEach(agreed::add);
		}
		if (!required.stream().allMatch(t -> agreed.contains(t.getId()))) {
			throw new BusinessException(ErrorCode.TERMS_AGREEMENT_REQUIRED);
		}

		List<Terms> agreedActive = active.stream().filter(t -> agreed.contains(t.getId())).toList();
		boolean notificationOptIn = agreedActive.stream()
			.anyMatch(t -> Terms.CODE_RECORD_NOTIFICATION.equals(t.getCode()));
		return new SignupTerms(agreedActive.stream().map(Terms::getId).toList(), notificationOptIn);
	}
}
