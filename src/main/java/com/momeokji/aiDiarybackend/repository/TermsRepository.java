package com.momeokji.aiDiarybackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.momeokji.aiDiarybackend.entity.Terms;

public interface TermsRepository extends JpaRepository<Terms, Long> {

	/** 가입 화면에 내보내는 약관. 시트에 보이는 순서다. */
	List<Terms> findByIsActiveTrueOrderByDisplayOrderAsc();
}
