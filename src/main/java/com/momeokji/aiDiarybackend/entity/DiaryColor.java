package com.momeokji.aiDiarybackend.entity;

import static lombok.AccessLevel.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@DynamicInsert
@IdClass(DiaryColorId.class)
@Table(name = "diary_color",
	indexes = @Index(name = "idx_color_diary", columnList = "diary_id"))
public class DiaryColor {

	@Id
	@Column(name = "diary_id", nullable = false)
	private Long diaryId;

	@Id
	@Column(name = "color_id", nullable = false)
	private Integer colorId; // 0, 1

	/** hex. 컬럼 이름이 color_name 인 것은 과거의 흔적이며, 사람이 읽는 이름은 {@link #name} 이다. */
	@Column(name = "color_name", nullable = false, length = 7) // "#AABBCC"
	private String colorName;

	/** 프롬프트가 준 색 이름("새싹 연두"). 구형 프롬프트 응답으로 만든 행은 null. */
	@Column(name = "name", length = 30)
	private String name;

	@Builder.Default
	@Column(name = "is_valid", nullable = false)
	private Boolean isValid = true;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

}