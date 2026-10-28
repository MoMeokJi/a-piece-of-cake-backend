package com.momeokji.aiDiarybackend.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.momeokji.aiDiarybackend.dto.request.AppVersionUpdateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.AppVersionResponseDto;
import com.momeokji.aiDiarybackend.dto.response.MinVersionResponseDto;
import com.momeokji.aiDiarybackend.entity.AppVersion;
import com.momeokji.aiDiarybackend.repository.AppVersionRepository;

import lombok.RequiredArgsConstructor;

/** 앱 최소 버전 조회(공개)와 관리자 등록. 버전 비교는 앱이 한다. */
@Service
@RequiredArgsConstructor
public class AppVersionService {

	private final AppVersionRepository appVersionRepository;
	private final Clock clock;
	private final TransactionTemplate transactionTemplate;

	/** 행이 없는 플랫폼은 null 로 내려간다. */
	@Transactional(readOnly = true)
	public MinVersionResponseDto getMinVersions() {
		return new MinVersionResponseDto(
			appVersionRepository.findById(AppVersion.IOS).map(AppVersionResponseDto::from).orElse(null),
			appVersionRepository.findById(AppVersion.ANDROID).map(AppVersionResponseDto::from).orElse(null));
	}

	/**
	 * upsert. {@code platform} 은 IOS 또는 AND, 그 외는 400 BAD_REQUEST.
	 *
	 * <p>같은 플랫폼의 최초 등록이 동시에 두 번 오면 한쪽의 INSERT 가 PK 충돌로 실패한다. 그 트랜잭션은 롤백되므로
	 * 새 트랜잭션에서 한 번 더 시도해 상대가 만든 행을 갱신한다. 같은 클래스 안의 {@code @Transactional} 은 프록시를
	 * 타지 않아 {@link TransactionTemplate} 으로 묶는다.
	 */
	public AppVersionResponseDto upsert(String platform, AppVersionUpdateRequestDto req) {
		if (!AppVersion.IOS.equals(platform) && !AppVersion.ANDROID.equals(platform)) {
			throw new IllegalArgumentException("platform 은 IOS 또는 AND 여야 합니다.");
		}
		try {
			return transactionTemplate.execute(status -> saveOrUpdate(platform, req));
		} catch (DataIntegrityViolationException raced) {
			return transactionTemplate.execute(status -> saveOrUpdate(platform, req));
		}
	}

	private AppVersionResponseDto saveOrUpdate(String platform, AppVersionUpdateRequestDto req) {
		LocalDateTime now = LocalDateTime.now(clock);
		AppVersion saved = appVersionRepository.findById(platform)
			.map(existing -> {
				existing.update(req.getMinVersion(), req.getLatestVersion(), req.getStoreUrl(), now);
				return existing;
			})
			.orElseGet(() -> appVersionRepository.saveAndFlush(
				AppVersion.create(platform, req.getMinVersion(), req.getLatestVersion(), req.getStoreUrl(), now)));
		return AppVersionResponseDto.from(saved);
	}
}
