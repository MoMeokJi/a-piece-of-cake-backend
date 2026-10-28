package com.momeokji.aiDiarybackend.dto.response;

import com.momeokji.aiDiarybackend.entity.AppVersion;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 플랫폼 하나의 버전 정보. */
@Getter
@AllArgsConstructor
public class AppVersionResponseDto {
	private String minVersion;
	private String latestVersion;
	private String storeUrl;

	public static AppVersionResponseDto from(AppVersion v) {
		return new AppVersionResponseDto(v.getMinVersion(), v.getLatestVersion(), v.getStoreUrl());
	}
}
