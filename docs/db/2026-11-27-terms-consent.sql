-- ============================================================
-- 약관 목록·동의 기록
-- 스펙: docs/superpowers/specs/2026-11-27-terms-consent-design.md
--
-- 실행 시점: 새 서버 배포 **전**에 실행한다. (ddl-auto=validate)
--   테이블만 만들고 초기 데이터를 넣지 않으면 신규 가입이 503 TERMS_NOT_CONFIGURED 로 막힌다.
--   반드시 아래 INSERT 까지 실행한다.
--
-- 배포 순서: 이 SQL → 서버 → 앱. 서버가 먼저 나가면 agreedTermsIds 를 보내지 않는
--   옛 앱 빌드로는 신규 가입이 400 TERMS_AGREEMENT_REQUIRED 로 막힌다(기존 회원 로그인은 영향 없음).
-- ============================================================

-- 가입 화면 약관 시트의 한 줄. 개정하면 같은 code 로 version 을 올린 새 행을 만들고 옛 행의 is_active 를 끈다.
CREATE TABLE terms (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(40)  NOT NULL,              -- SERVICE, PRIVACY, AI_OVERSEAS_TRANSFER, RECORD_NOTIFICATION
    version       INT          NOT NULL,              -- 같은 code 안에서 1부터
    title         VARCHAR(100) NOT NULL,              -- 시트에 보이는 문구
    required      TINYINT      NOT NULL,
    display_order INT          NOT NULL,
    content_path  VARCHAR(200) NULL,                  -- 본문 페이지 경로. 본문이 없으면 NULL
    is_active     TINYINT      NOT NULL,              -- 지금 가입 화면에 내보내는 행인지
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_terms_code_version (code, version)
);

-- 회원이 가입할 때 동의한 약관. 탈퇴하면 서버가 지운다.
CREATE TABLE member_terms_agreement (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  VARCHAR(255) NOT NULL,
    terms_id   BIGINT       NOT NULL,
    agreed_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_terms (member_id, terms_id),
    KEY idx_member_terms_member (member_id)
);

-- 초기 약관. RECORD_NOTIFICATION 에 동의하면 가입 시점에 member.notification_enabled 가 켜진다(코드가 이 code 를 본다).
INSERT INTO terms (code, version, title, required, display_order, content_path, is_active, created_at) VALUES
    ('SERVICE',              1, '서비스 이용약관',                              1, 1, '/terms',      1, NOW(6)),
    ('PRIVACY',              1, '개인정보 수집 및 이용',                        1, 2, '/privacy',    1, NOW(6)),
    ('AI_OVERSEAS_TRANSFER', 1, 'AI 일기 생성을 위한 개인정보 국외 이전 동의',  1, 3, '/ai-consent', 1, NOW(6)),
    ('RECORD_NOTIFICATION',  1, '기록 알림 받기',                               0, 4, NULL,          1, NOW(6));
