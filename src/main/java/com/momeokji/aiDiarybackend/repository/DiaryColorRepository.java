package com.momeokji.aiDiarybackend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.momeokji.aiDiarybackend.entity.DiaryColor;
import com.momeokji.aiDiarybackend.entity.DiaryColorId;

public interface DiaryColorRepository extends JpaRepository<DiaryColor, DiaryColorId> {
	List<DiaryColor> findByDiaryIdOrderByColorIdAsc(Long diaryId);

	List<DiaryColor> findByDiaryIdInOrderByDiaryIdAscColorIdAsc(List<Long> diaryIds);

	@Modifying
	@Query("""
        update DiaryColor c
           set c.isValid = false, c.deletedAt = :now
         where c.diaryId = :diaryId
           and c.isValid = true
    """)
	int softDeleteByDiaryId(@Param("diaryId") Long diaryId,
		@Param("now") LocalDateTime now);

	@Modifying
	@Query("""
        update DiaryColor c
           set c.isValid = false, c.deletedAt = :now
         where c.diaryId in :diaryIds
           and c.isValid = true
    """)
	int softDeleteByDiaryIdIn(@Param("diaryIds") List<Long> diaryIds,
		@Param("now") LocalDateTime now);

	@Modifying
	@Query("""
    delete from DiaryColor c
    where c.isValid = false
      and c.deletedAt <= :threshold
""")
	int deleteExpired(@Param("threshold") LocalDateTime threshold);
}