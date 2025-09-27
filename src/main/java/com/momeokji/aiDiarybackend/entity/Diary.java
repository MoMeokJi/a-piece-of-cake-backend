package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "diary")
public class Diary {

	@Id
	@Column(name = "diary_id")
	private String diaryId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(columnDefinition = "TEXT")
	private String content;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "image_url", columnDefinition = "TEXT")
	private String imageUrl;

	@Column(columnDefinition = "TEXT")
	private String summary;

	@Column(name = "feedback_msg", columnDefinition = "TEXT")
	private String feedbackMsg;

	@Column(name = "recommand_music")
	private String recommandMusic;

	@Column(name = "recoomandColors")
	private String recoomandColors;


	@Builder
	public Diary(String content, LocalDateTime createdAt, String diaryId, String feedbackMsg, String imageUrl,
		String recommandMusic, String recoomandColors, String summary, String userId) {
		this.content = content;
		this.createdAt = createdAt;
		this.diaryId = diaryId;
		this.feedbackMsg = feedbackMsg;
		this.imageUrl = imageUrl;
		this.recommandMusic = recommandMusic;
		this.recoomandColors = recoomandColors;
		this.summary = summary;
		this.userId = userId;
	}


}
