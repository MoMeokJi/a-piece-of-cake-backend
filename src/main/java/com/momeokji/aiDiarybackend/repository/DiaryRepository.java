package com.momeokji.aiDiarybackend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.momeokji.aiDiarybackend.entity.Diary;

@Repository
public interface DiaryRepository extends JpaRepository<Diary,Long> {
	Page<Diary> findByUserId(String userId, Pageable pageable);

	List<Diary> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
		String userId, LocalDateTime from, LocalDateTime to
	);

	Optional<Diary> findByDiaryIdAndUserId(Long diaryId, String userId);
}
