package com.momeokji.aiDiarybackend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.momeokji.aiDiarybackend.entity.DiaryImage;
import com.momeokji.aiDiarybackend.entity.DiaryImageId;

public interface DiaryImageRepository extends JpaRepository<DiaryImage, DiaryImageId> {
	List<DiaryImage> findByDiaryIdOrderByImageIdAsc(Long diaryId);

	@Modifying
	@Query("""
        update DiaryImage i
           set i.isValid = false, i.deletedAt = :now
         where i.diaryId = :diaryId
           and i.isValid = true
    """)
	int softDeleteByDiaryId(@Param("diaryId") Long diaryId,
		@Param("now") LocalDateTime now);

	@Modifying
	@Query("""
        update DiaryImage i
           set i.isValid = false, i.deletedAt = :now
         where i.diaryId in :diaryIds
           and i.isValid = true
    """)
	int softDeleteByDiaryIdIn(@Param("diaryIds") List<Long> diaryIds,
		@Param("now") LocalDateTime now);

	@Query("""
        select i
          from DiaryImage i
         where i.isValid = false
           and i.deletedAt <= :threshold
    """)
	List<DiaryImage> findExpiredImages(@Param("threshold") LocalDateTime threshold);

	@Modifying
	@Query("""
    delete from DiaryImage i
    where i.isValid = false
      and i.deletedAt <= :threshold
""")
	int deleteExpired(@Param("threshold") LocalDateTime threshold);
}
