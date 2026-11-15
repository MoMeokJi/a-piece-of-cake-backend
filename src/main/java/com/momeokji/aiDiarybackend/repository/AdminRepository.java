package com.momeokji.aiDiarybackend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.momeokji.aiDiarybackend.entity.Admin;

@Repository
public interface AdminRepository extends JpaRepository<Admin, Long> {

	Optional<Admin> findByAdminId(String adminId);

	Optional<Admin> findByAdminIdAndIsValidTrue(String adminId);

	List<Admin> findByIsSuperFalseAndDeletedAtIsNull();
}