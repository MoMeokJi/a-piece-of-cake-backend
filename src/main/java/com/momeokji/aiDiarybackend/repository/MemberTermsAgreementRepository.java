package com.momeokji.aiDiarybackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.entity.MemberTermsAgreement;

public interface MemberTermsAgreementRepository extends JpaRepository<MemberTermsAgreement, Long> {

	List<MemberTermsAgreement> findByMemberId(String memberId);

	@Transactional
	void deleteByMemberId(String memberId);
}
