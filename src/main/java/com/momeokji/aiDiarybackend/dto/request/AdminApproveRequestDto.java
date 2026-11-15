package com.momeokji.aiDiarybackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AdminApproveRequestDto {

	@NotBlank(message = "adminId는 필수입니다.")
	private String adminId;
}