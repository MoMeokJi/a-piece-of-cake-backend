package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가입 화면의 약관 시트에 내보내는 한 줄(스펙 2026-11-27 4.1).
 *
 * <p>약관을 개정하면 행을 고치지 않고 같은 {@code code} 로 {@code version} 을 올린 새 행을 만든 뒤 옛 행을 끈다.
 * 동의 기록({@link MemberTermsAgreement})이 행 ID 를 가리키므로 어느 버전에 동의했는지가 남는다.
 * 본문은 DB 에 없고 {@code contentPath} 의 HTML 페이지({@code classpath:site/*.html})에 있다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
	name = "terms",
	uniqueConstraints = @UniqueConstraint(name = "uk_terms_code_version", columnNames = {"code", "version"})
)
public class Terms {

	/** 이 약관에 동의하면 가입 시점에 기록 알림({@code member.notification_enabled})을 켠다. */
	public static final String CODE_RECORD_NOTIFICATION = "RECORD_NOTIFICATION";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/** 약관 종류. SERVICE, PRIVACY, AI_OVERSEAS_TRANSFER, RECORD_NOTIFICATION. 앱에는 내려주지 않는다. */
	@Column(name = "code", nullable = false, length = 40)
	private String code;

	/** 같은 code 안에서 1부터 올라간다. */
	@Column(name = "version", nullable = false)
	private Integer version;

	/** 시트에 보이는 문구. */
	@Column(name = "title", nullable = false, length = 100)
	private String title;

	@Column(name = "required", nullable = false, columnDefinition = "TINYINT")
	private Boolean required;

	@Column(name = "display_order", nullable = false)
	private Integer displayOrder;

	/** 본문 페이지 경로({@code /terms} 등). 본문이 없는 항목은 NULL. */
	@Column(name = "content_path", length = 200)
	private String contentPath;

	/** 지금 가입 화면에 내보내는 행인지. */
	@Column(name = "is_active", nullable = false, columnDefinition = "TINYINT")
	private Boolean isActive;

	@Column(name = "created_at", updatable = false, nullable = false)
	private LocalDateTime createdAt;

	public static Terms create(String code, int version, String title, boolean required, int displayOrder,
		String contentPath, LocalDateTime now) {
		Terms terms = new Terms();
		terms.code = code;
		terms.version = version;
		terms.title = title;
		terms.required = required;
		terms.displayOrder = displayOrder;
		terms.contentPath = contentPath;
		terms.isActive = true;
		terms.createdAt = now;
		return terms;
	}

	/** 개정이나 폐지로 가입 화면에서 내린다. 이미 받은 동의 기록은 그대로 남는다. */
	public void deactivate() {
		this.isActive = false;
	}
}
