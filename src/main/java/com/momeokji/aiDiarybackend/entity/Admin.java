package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;

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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@DynamicInsert
@Table(name = "admin")
public class Admin {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "admin_id", nullable = false, unique = true)
	private String adminId;

	@Column(name = "admin_pwd", nullable = false)
	private String adminPwd;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "is_valid", nullable = false, columnDefinition = "TINYINT")
	@ColumnDefault("0")
	private Boolean isValid;

	@Column(name = "is_super", nullable = false, columnDefinition = "TINYINT")
	@ColumnDefault("0")
	private Boolean isSuper;

	@Builder
	public Admin(String adminId, String adminPwd, LocalDateTime createdAt, Boolean isValid, Boolean isSuper) {
		this.adminId = adminId;
		this.adminPwd = adminPwd;
		this.createdAt = createdAt;
		this.isValid = isValid;
		this.isSuper = isSuper;
	}

	public void approve() {
		this.isValid = true;
	}

	public void delete(LocalDateTime deletedAt) {
		this.isValid = false;
		this.deletedAt = deletedAt;
	}
}
