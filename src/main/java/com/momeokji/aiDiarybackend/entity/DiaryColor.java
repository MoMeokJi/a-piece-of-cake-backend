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

	@Column(name = "color_name", nullable = false, length = 7) // "#AABBCC"
	private String colorName;

	@Column(name = "is_valid", nullable = false)
	private Boolean isValid = true;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

}