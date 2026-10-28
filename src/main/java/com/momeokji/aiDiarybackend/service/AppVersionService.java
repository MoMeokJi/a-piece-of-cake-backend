package com.momeokji.aiDiarybackend.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	/** 행이 없는 플랫폼은 null 로 내려간다. */
	@Transactional(readOnly = true)
	public MinVersionResponseDto getMinVersions() {
		return new MinVersionResponseDto(
			appVersionRepository.findById(AppVersion.IOS).map(AppVersionResponseDto::from).orElse(null),
			appVersionRepository.findById(AppVersion.ANDROID).map(AppVersionResponseDto::from).orElse(null));
	}

	/** upsert. {@code platform} 은 IOS 또는 AND, 그 외는 400 BAD_REQUEST. */
	@Transactional
	public AppVersionResponseDto upsert(String platform, AppVersionUpdateRequestDto req) {
		if (!AppVersion.IOS.equals(platform) && !AppVersion.ANDROID.equals(platform)) {
			throw new IllegalArgumentException("platform 은 IOS 또는 AND 여야 합니다.");
		}
		LocalDateTime now = LocalDateTime.now(clock);
		AppVersion saved = appVersionRepository.findById(platform)
			.map(existing -> {
				existing.update(req.getMinVersion(), req.getLatestVersion(), req.getStoreUrl(), now);
				return existing;
			})
			.orElseGet(() -> appVersionRepository.save(
				AppVersion.create(platform, req.getMinVersion(), req.getLatestVersion(), req.getStoreUrl(), now)));
		return AppVersionResponseDto.from(saved);
	}
}
