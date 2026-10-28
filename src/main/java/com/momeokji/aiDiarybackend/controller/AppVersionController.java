package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.response.MinVersionResponseDto;
import com.momeokji.aiDiarybackend.service.AppVersionService;

import lombok.RequiredArgsConstructor;

/**
 * 앱 최소 버전 (공개). 앱이 시작할 때 읽어 자기 버전이 {@code minVersion} 보다 낮으면 강제 업데이트를 건다.
 *
 * <p>배포 순서: 이 엔드포인트가 포함된 서버가 먼저 나가고, 이것을 읽는 앱 버전이 퍼진 뒤에야
 * 다음 깨지는 변경에서 {@code minVersion} 을 올릴 수 있다.
 */
@RestController
@RequiredArgsConstructor
public class AppVersionController {

	private final AppVersionService appVersionService;

	@GetMapping("/app/min-version")
	public ResponseEntity<MinVersionResponseDto> minVersion() {
		return ResponseEntity.ok(appVersionService.getMinVersions());
	}
}
