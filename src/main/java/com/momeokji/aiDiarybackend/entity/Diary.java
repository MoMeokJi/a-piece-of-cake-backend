package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@DynamicInsert
@Table(name = "diary",
	indexes = {
	@Index(name = "idx_diary_user", columnList = "user_id"), @Index(name = "idx_diary_created", columnList = "created_at")
	})
public class Diary {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "diary_id")
	private Long diaryId;

	@Column(name = "user_id", nullable = false, length = 255)
	private String userId;

	@Column(columnDefinition = "TEXT")
	private String content;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false, nullable = false)
	private LocalDateTime createdAt;

	@Column(columnDefinition = "TEXT")
	private String summary;

	@Column(name = "feedback_msg", columnDefinition = "TEXT")
	private String feedbackMsg;

	@Column(name = "recommand_music")
	private Long recommandMusic;

	@Column(name = "is_valid", nullable = false)
	private Boolean isValid = true;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	public void updateFeedbackMsg(String feedbackMsg) {
		this.feedbackMsg = feedbackMsg;
	}
	public void updateContent(String content) { this.content = content; }


}