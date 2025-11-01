package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
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

	@Column(name = "recommand_music", length = 255)
	private String recommandMusic;

}