package com.momeokji.aiDiarybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@IdClass(DiaryImageId.class)
@Table(name = "diary_image",
	indexes = @Index(name = "idx_image_diary", columnList = "diary_id"))
public class DiaryImage {

	@Id
	@Column(name = "diary_id", nullable = false)
	private Long diaryId;

	@Id
	@Column(name = "image_id", nullable = false)
	private Integer imageId;

	@Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
	private String imageUrl;
}