package com.momeokji.aiDiarybackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.momeokji.aiDiarybackend.entity.Diary;

@Repository
public interface DiaryRepository extends JpaRepository<Diary,Long> {
	Page<Diary> findByUserIdAndIsValidTrue(String userId, Pageable pageable);

	List<Diary> findByUserIdAndIsValidTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(String userId, LocalDateTime from, LocalDateTime to);

	Optional<Diary> findByDiaryIdAndUserIdAndIsValidTrue(Long diaryId, String userId);

	@Modifying
	@Query("""
      update Diary d
         set d.isValid = false, d.deletedAt = :now
       where d.diaryId = :diaryId
         and d.userId  = :userId
         and d.isValid = true
      """)
	int softDeleteOne(@Param("userId") String userId,
		@Param("diaryId") Long diaryId,
		@Param("now") LocalDateTime now);

	@Modifying
	@Query("""
      update Diary d
         set d.isValid = false, d.deletedAt = :now
       where d.userId = :userId
         and d.isValid = true
      """)
	int softDeleteAllOfUser(@Param("userId") String userId,
		@Param("now") LocalDateTime now);

	//pageable뺀 findbyId
	@Query("""
        select d.diaryId
          from Diary d
         where d.userId = :userId
           and d.isValid = true
    """)
	List<Long> findAliveIdsByUserId(@Param("userId") String userId);

	List<Diary> findTop100ByIsValidTrueAndFeedbackMsgIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
		LocalDateTime before
	);

	Optional<Diary> findTop1ByUserIdAndIsValidTrueOrderByCreatedAtDesc(String userId);
}
