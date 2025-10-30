package com.momeokji.aiDiarybackend.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DiaryColorId implements Serializable {
	private Long diaryId;
	private Integer colorId;

}