package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** {@code PUT /admin/app/versions/{platform}} 요청. 세 값을 항상 같이 보낸다(보내지 않은 값은 null 로 저장). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AppVersionUpdateRequestDto {

	@NotBlank(message = "minVersion은 필수입니다.")
	@Pattern(regexp = RequestPatterns.VERSION, message = RequestPatterns.VERSION_MESSAGE)
	private String minVersion;

	@Pattern(regexp = RequestPatterns.VERSION, message = RequestPatterns.VERSION_MESSAGE)
	private String latestVersion;

	@Size(max = 500, message = "storeUrl은 500자 이하여야 합니다.")
	private String storeUrl;
}
