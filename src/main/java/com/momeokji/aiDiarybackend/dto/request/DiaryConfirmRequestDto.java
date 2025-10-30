package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Getter
@Builder
@Jacksonized
public class DiaryConfirmRequestDto {
	@NotBlank
	private final String text;

	@Size(min = 1, max = 5)
	private final List<@NotBlank String> images;
}