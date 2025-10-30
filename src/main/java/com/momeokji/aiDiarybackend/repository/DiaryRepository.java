package com.momeokji.aiDiarybackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.momeokji.aiDiarybackend.entity.Diary;

@Repository
public interface DiaryRepository extends JpaRepository<Diary,Long> {


}
