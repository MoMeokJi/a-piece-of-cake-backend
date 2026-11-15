-- ============================================================
-- main → dev 사이에 생긴 스키마 차이 (2026-11-13 정리)
--
-- 배경: 운영 서버는 main 브랜치 코드로 돌고 있다(2026-11-13 확인: dev 전용 경로 POST /admins/login 이 없음).
--   docs/db 의 다른 파일들은 dev → feat/social-login 변경만 담고 있어서, dev 에서 먼저 추가된
--   아래 두 가지가 운영 DB 에 없을 수 있다. ddl-auto=validate 라서 없으면 새 서버가 기동하지 않는다.
--
-- 실행 시점: 새 서버 배포 **전**, 그리고 2026-10-19-social-login-schema.sql **보다 먼저** 실행한다.
--   (그 파일이 admin 테이블에 token_version 컬럼을 추가하기 때문이다)
--
-- 실행 전 확인: 운영 DB 에 이미 있으면 해당 문장은 건너뛴다.
--   SHOW TABLES LIKE 'admin';
--   SHOW COLUMNS FROM member LIKE 'created_at';
-- ============================================================

-- 1. admin: 관리자 계정. token_version 은 2026-10-19-social-login-schema.sql 이 추가한다.
CREATE TABLE IF NOT EXISTS admin (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id   VARCHAR(255) NOT NULL,
    admin_pwd  VARCHAR(255) NOT NULL,
    is_valid   TINYINT      NOT NULL DEFAULT 0,
    is_super   TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL,
    deleted_at DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_admin_id (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2. member.created_at: 가입 시각. 기존 행은 실행 시각으로 채워진다
--    (기존 익명 회원은 배포 직후 cleanup 스크립트로 정리되므로 값의 정확성은 중요하지 않다).
ALTER TABLE member ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- 3. 최초 슈퍼 관리자: 서버에 자동 생성 로직이 없다. 새 서버 배포 후
--    POST /admin/auth/signup 으로 가입한 다음 아래로 직접 승인한다.
--    UPDATE admin SET is_valid = 1, is_super = 1 WHERE admin_id = '<가입한 ID>';
