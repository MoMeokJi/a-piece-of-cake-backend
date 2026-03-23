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
}