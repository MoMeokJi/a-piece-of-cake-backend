package com.momeokji.aiDiarybackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.momeokji.aiDiarybackend.dto.response.MinVersionResponseDto;
import com.momeokji.aiDiarybackend.service.AppVersionService;

import io.swagger.v3.oas.annotations.Operation;
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

	@Operation(summary = "앱 최소 버전 (공개)", description = """
		토큰 없이 호출한다. 앱은 자기 플랫폼의 `minVersion` 과 자기 버전을 semver 로 비교해 낮으면 강제 업데이트를 건다.
		등록되지 않은 플랫폼은 null 이다. 버전은 `x.y.z` 형식이다.
		배포 순서: 이 API 가 포함된 서버가 먼저 나가고, 이것을 읽는 앱 버전이 퍼진 뒤에야 다음 깨지는 변경에서 `minVersion` 을 올릴 수 있다.
		""")
	@GetMapping("/app/min-version")
	public ResponseEntity<MinVersionResponseDto> minVersion() {
		return ResponseEntity.ok(appVersionService.getMinVersions());
	}
}
