package com.momeokji.aiDiarybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "question_list")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "question_id")
	private Integer id;

	@Column(name = "content")
	private String content;

	@Column(name = "category")
	private String category;

	@Builder
	public Question(String content, String category) {
		this.content = content;
		this.category = category;
	}

	public void update(String content, String category) {
		this.content = content;
		this.category = category;
	}
}

