package com.momeokji.aiDiarybackend.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DiaryImageId implements Serializable {
	private Long diaryId;
	private Integer imageId; // 0..4
}
