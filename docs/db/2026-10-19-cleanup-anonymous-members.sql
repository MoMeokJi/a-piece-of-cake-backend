-- ============================================================
-- 기존 익명(기기 기반) 회원 정리 (스펙 8.1)
--
-- 실행 시점: 새 서버 배포 **직후**, 소셜 로그인 회원이 생기기 **전**에 1회 실행한다.
--   (소셜 회원이 생긴 뒤에 돌리면 그 회원까지 soft delete 된다.)
--
-- 하는 일: is_valid = 1 인 기존 회원 전부와 그 회원의 일기, 색상, 이미지를 soft delete 한다.
--   기존 탈퇴와 같은 방식이라, 이후 DiaryDeleteScheduler 가 3일 뒤 DB 에서 완전히 지우고 S3 이미지도 정리한다.
--
-- 실행 전 건수 확인:
--   SELECT COUNT(*) FROM member WHERE is_valid = 1;
--   SELECT COUNT(*) FROM diary d JOIN member m ON m.member_id = d.user_id WHERE m.is_valid = 1 AND d.is_valid = 1;
-- ============================================================

START TRANSACTION;

SET @now = NOW(6);

-- 일기에 딸린 색상/이미지 (아직 살아 있는 일기 기준)
UPDATE diary_color dc
  JOIN diary d  ON d.diary_id = dc.diary_id
  JOIN member m ON m.member_id = d.user_id
   SET dc.is_valid = 0, dc.deleted_at = @now
 WHERE m.is_valid = 1 AND d.is_valid = 1 AND dc.is_valid = 1;

UPDATE diary_image di
  JOIN diary d  ON d.diary_id = di.diary_id
  JOIN member m ON m.member_id = d.user_id
   SET di.is_valid = 0, di.deleted_at = @now
 WHERE m.is_valid = 1 AND d.is_valid = 1 AND di.is_valid = 1;

-- 일기
UPDATE diary d
  JOIN member m ON m.member_id = d.user_id
   SET d.is_valid = 0, d.deleted_at = @now
 WHERE m.is_valid = 1 AND d.is_valid = 1;

-- 회원
UPDATE member
   SET is_valid = 0, deleted_at = @now
 WHERE is_valid = 1;

COMMIT;

-- ------------------------------------------------------------
-- Redis 정리 (SQL 로는 불가). 유저별 refsets 키 패턴: aidiary:prod:refset:<member_id>
-- 운영 Redis 에서 아래를 실행한다 (KEYS 대신 SCAN 사용):
--
--   redis-cli --scan --pattern 'aidiary:prod:refset:*' | xargs -r redis-cli del
--
-- 호스트/포트/인증이 필요하면 각 redis-cli 에 -h/-p/-a 옵션을 붙인다.
-- ------------------------------------------------------------
