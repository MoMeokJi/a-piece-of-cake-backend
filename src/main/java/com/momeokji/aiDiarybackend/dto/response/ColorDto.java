package com.momeokji.aiDiarybackend.dto.response;

import com.momeokji.aiDiarybackend.entity.DiaryColor;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 일기 색 하나. {@code name} 은 프롬프트가 이름을 주기 전의 일기에서는 null 이다. */
@Getter
@AllArgsConstructor
public class ColorDto {
	private String name;
	private String hex;

	public static ColorDto from(DiaryColor color) {
		return new ColorDto(color.getName(), color.getColorName());
	}
}
