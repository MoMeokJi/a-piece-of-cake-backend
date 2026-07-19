package com.momeokji.aiDiarybackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.momeokji.aiDiarybackend.entity.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member,String> {

	long countByIsValidTrue();

	Optional<Member> findByDeviceId(String deviceId);

	Optional<Member> findByDeviceIdAndIsValidTrue(String deviceId);

	Optional<Member> findByMemberIdAndIsValidTrue(String memberId);

	List<Member> findByIsValidTrueAndLastActiveAtBefore(LocalDateTime time);

	@Modifying
	@Query("""
      update Member m
         set m.isValid = false, m.deletedAt = :now
       where m.memberId = :memberId
         and m.isValid = true
      """)
	int softDeleteOne(@Param("memberId") String memberId,
		@Param("now") LocalDateTime now);

	List<Member> findByIsValidTrue();

	@Modifying
	@Query("""
		update Member m
		   set m.lastActiveAt = :now
		 where m.memberId = :memberId
		   and m.isValid = true
		""")
	int changeLastActiveAt(@Param("memberId") String memberId,
		@Param("now") LocalDateTime now);

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