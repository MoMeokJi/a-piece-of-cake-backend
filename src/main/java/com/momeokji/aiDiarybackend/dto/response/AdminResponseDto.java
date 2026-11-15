package com.momeokji.aiDiarybackend.dto.response;

import java.time.LocalDateTime;

import com.momeokji.aiDiarybackend.entity.Admin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminResponseDto {

	private String adminId;
	private LocalDateTime createdAt;
	private Boolean isValid;
	private Boolean isSuper;

	public static AdminResponseDto from(Admin admin) {
		return AdminResponseDto.builder()
			.adminId(admin.getAdminId())
			.createdAt(admin.getCreatedAt())
			.isValid(admin.getIsValid())
			.isSuper(admin.getIsSuper())
			.build();
	}
}