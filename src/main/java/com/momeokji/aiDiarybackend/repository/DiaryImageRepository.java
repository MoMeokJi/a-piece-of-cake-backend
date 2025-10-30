package com.momeokji.aiDiarybackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.momeokji.aiDiarybackend.entity.DiaryImage;
import com.momeokji.aiDiarybackend.entity.DiaryImageId;

public interface DiaryImageRepository extends JpaRepository<DiaryImage, DiaryImageId> {
	List<DiaryImage> findByDiaryIdOrderByImageIdAsc(Long diaryId);
}
