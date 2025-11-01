package com.momeokji.aiDiarybackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.momeokji.aiDiarybackend.entity.DiaryColor;
import com.momeokji.aiDiarybackend.entity.DiaryColorId;

public interface DiaryColorRepository extends JpaRepository<DiaryColor, DiaryColorId> {
	List<DiaryColor> findByDiaryIdOrderByColorIdAsc(Long diaryId);
}