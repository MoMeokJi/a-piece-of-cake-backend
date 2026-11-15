package com.momeokji.aiDiarybackend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.entity.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member,String> {

	long countByIsValidTrue();

	Optional<Member> findByMemberIdAndIsValidTrue(String memberId);

	/** 탈퇴 처리. 이용자가 지은 닉네임을 지우고 알림 설정을 기본값(꺼짐)으로 돌려, 남는 행에 임의 ID 와 가입·탈퇴 시각만 남긴다. */
	@Transactional
	@Modifying
	@Query("""
      update Member m
         set m.isValid = false, m.deletedAt = :now, m.nickname = null, m.notificationEnabled = false
       where m.memberId = :memberId
         and m.isValid = true
      """)
	int softDeleteOne(@Param("memberId") String memberId,
		@Param("now") LocalDateTime now);

	List<Member> findByIsValidTrue();

	/** 리마인드 대상: 유효하고 기록 알림을 켠 회원. */
	List<Member> findByIsValidTrueAndNotificationEnabledTrue();

	/** 여러 회원을 한 번에. 공감 스케줄러가 일기마다 조회하지 않기 위해 쓴다. */
	List<Member> findByMemberIdInAndIsValidTrue(Collection<String> memberIds);

	@Query(
		value = """
		SELECT
			DATE(m.created_at) AS date,
			COUNT(*) AS totalCount
		FROM member m
		WHERE m.is_valid = 1
		  AND m.created_at >= :from
		  AND m.created_at < :to
		GROUP BY DATE(m.created_at)
		ORDER BY DATE(m.created_at)
		""",
		nativeQuery = true
	)
	List<DailyCountStatistics> countDailySignUps(
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to
	);
}