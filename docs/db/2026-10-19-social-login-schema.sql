-- ============================================================
-- 소셜 로그인 + 인증 구조 개편 스키마 (스펙 3.2 ~ 3.4)
--
-- 실행 시점: 새 서버 배포 **전**에 실행한다.
--   ddl-auto=validate 라서 이 테이블/컬럼이 없으면 새 서버가 기동하지 않는다.
--
-- 사전 확인: 기존 member 테이블의 device_id / mobile_os / preference / last_active_at 는
--   엔티티에서 제거되지만 컬럼은 남겨둔다. 새 회원 INSERT 가 member_id 와 기본값 컬럼만 넣으므로
--   이 네 컬럼이 NULL 허용인지 확인한다 (`SHOW CREATE TABLE member;`). 로컬 DB 기준으로는 모두 NULL 허용이다.
--   NOT NULL 이면 아래를 먼저 실행한다:
--     ALTER TABLE member MODIFY device_id VARCHAR(255) NULL,
--                        MODIFY mobile_os VARCHAR(255) NULL,
--                        MODIFY preference VARCHAR(255) NULL,
--                        MODIFY last_active_at DATETIME(6) NULL;
-- ============================================================

-- 3.2 social_account: 소셜 계정 ↔ 회원 연결
CREATE TABLE social_account (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    member_id           VARCHAR(255) NOT NULL,
    provider            VARCHAR(20)  NOT NULL,                -- GOOGLE, APPLE
    provider_user_id    VARCHAR(255) NOT NULL,                -- ID 토큰의 sub
    email               VARCHAR(320) NULL,                    -- 참고용
    apple_refresh_token VARCHAR(1024) NULL,                   -- AES-GCM 암호문(base64). 애플이 아니면 NULL
    created_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_social_account_provider_user (provider, provider_user_id),
    KEY idx_social_account_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3.3 member_device: 기기 1대 = 로그인 세션 1개
CREATE TABLE member_device (
    device_id               VARCHAR(36)  NOT NULL,            -- 앱이 만든 UUID
    member_id               VARCHAR(255) NOT NULL,            -- 지금 이 기기에 로그인한 회원
    os                      VARCHAR(10)  NOT NULL,            -- AND, IOS
    fcm_token               VARCHAR(512) NULL,                -- 푸시 주소
    refresh_token_hash      VARCHAR(64)  NOT NULL,            -- 현재 refresh 의 SHA-256(hex)
    prev_refresh_token_hash VARCHAR(64)  NULL,                -- 직전 refresh 해시(재사용 감지용)
    rotated_at              DATETIME(6)  NULL,                -- 마지막 회전 시각
    refresh_expires_at      DATETIME(6)  NOT NULL,
    last_used_at            DATETIME(6)  NOT NULL,            -- 마지막 로그인 또는 refresh 시각
    created_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (device_id),
    UNIQUE KEY uk_member_device_fcm_token (fcm_token),
    UNIQUE KEY uk_member_device_refresh_hash (refresh_token_hash),
    KEY idx_member_device_member (member_id),
    KEY idx_member_device_prev_hash (prev_refresh_token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3.4 admin.token_version: 관리자 토큰 무효화용
ALTER TABLE admin ADD COLUMN token_version INT NOT NULL DEFAULT 0;
