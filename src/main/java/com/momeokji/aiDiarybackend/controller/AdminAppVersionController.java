package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.request.AppVersionUpdateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.AppVersionResponseDto;
import com.momeokji.aiDiarybackend.service.AppVersionService;

import lombok.RequiredArgsConstructor;

/** 관리자가 플랫폼별 최소 버전을 등록·수정한다. {@code /admin/**} 라 관리자 토큰만 통과한다. */
@RestController
@RequestMapping("/admin/app/versions")
@RequiredArgsConstructor
public class AdminAppVersionController {

	private final AppVersionService appVersionService;

	/** upsert. platform 은 IOS 또는 AND. */
	@PutMapping("/{platform}")
	public ResponseEntity<AppVersionResponseDto> upsert(@PathVariable String platform,
		@Validated @RequestBody AppVersionUpdateRequestDto req) {
		return ResponseEntity.ok(appVersionService.upsert(platform, req));
	}
}
