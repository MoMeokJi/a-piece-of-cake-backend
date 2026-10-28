package com.momeokji.aiDiarybackend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 플랫폼별 앱 최소 버전. 앱은 {@code GET /app/min-version} 으로 읽어 강제 업데이트를 건다.
 * 관리자가 운영 중에 바꾸므로 설정 파일이 아니라 DB 에 둔다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "app_version")
public class AppVersion {

	public static final String IOS = "IOS";
	public static final String ANDROID = "AND";

	/** IOS 또는 AND */
	@Id
	@Column(name = "platform", length = 10)
	private String platform;

	/** 이 버전 미만은 강제 업데이트. {@code x.y.z} */
	@Column(name = "min_version", nullable = false, length = 20)
	private String minVersion;

	/** 스토어 최신 버전. 앱이 "업데이트 가능" 안내에 쓴다. 선택. */
	@Column(name = "latest_version", length = 20)
	private String latestVersion;

	@Column(name = "store_url", length = 500)
	private String storeUrl;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public static AppVersion create(String platform, String minVersion, String latestVersion, String storeUrl, LocalDateTime now) {
		AppVersion v = new AppVersion();
		v.platform = platform;
		v.update(minVersion, latestVersion, storeUrl, now);
		return v;
	}

	/** 전달값으로 전부 교체한다(null 포함). 관리자 화면이 세 값을 항상 같이 보낸다. */
	public void update(String minVersion, String latestVersion, String storeUrl, LocalDateTime now) {
		this.minVersion = minVersion;
		this.latestVersion = latestVersion;
		this.storeUrl = storeUrl;
		this.updatedAt = now;
	}
}
