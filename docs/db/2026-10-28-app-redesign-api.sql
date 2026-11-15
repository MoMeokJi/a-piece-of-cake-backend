-- ============================================================
-- 앱 리디자인 대응 API 스키마 변경
-- 스펙: docs/superpowers/specs/2026-10-28-app-redesign-api-design.md
--
-- 실행 시점: 새 서버 배포 **전**에 실행한다. (ddl-auto=validate)
-- 기존 유저와 일기는 버리기로 했으므로 기존 행의 값은 DEFAULT 로 채워져도 무방하다.
-- ============================================================

-- 1. 일기 타입 (FREE / QNA). 기존 행은 알 수 없으므로 FREE 로 둔다.
ALTER TABLE diary ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'FREE';

-- 9. 색상 이름. color_name 컬럼은 hex 를 담고 있으며 이름을 바꾸지 않는다. 프롬프트가 이름을 주기 전 일기는 NULL.
ALTER TABLE diary_color ADD COLUMN name VARCHAR(30) NULL;

-- 10. 음악 재생 시간(초). 기존 행은 scripts/backfill-music-duration.sh 로 채운다.
ALTER TABLE music ADD COLUMN duration_seconds INT NULL;

-- 4. 기록 알림(리마인드·공감) 수신 여부. 기본 꺼짐. 앱이 약관의 [선택] 체크값을 가입 직후 PUT 으로 보낸다.
ALTER TABLE member ADD COLUMN notification_enabled TINYINT NOT NULL DEFAULT 0;

-- 17. 앱 최소 버전. 관리자 API(PUT /admin/app/versions/{platform})로 채운다. platform 은 IOS / AND.
CREATE TABLE app_version (
  platform       VARCHAR(10)  NOT NULL,
  min_version    VARCHAR(20)  NOT NULL,
  latest_version VARCHAR(20)  NULL,
  store_url      VARCHAR(500) NULL,
  updated_at     DATETIME     NOT NULL,
  PRIMARY KEY (platform)
);
