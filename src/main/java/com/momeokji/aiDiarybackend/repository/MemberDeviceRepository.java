package com.momeokji.aiDiarybackend.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.momeokji.aiDiarybackend.entity.MemberDevice;

import jakarta.persistence.LockModeType;

@Repository
public interface MemberDeviceRepository extends JpaRepository<MemberDevice, String> {

	/** refresh 회전용. 같은 기기의 동시 요청을 직렬화하기 위해 행 잠금을 건다(SELECT ... FOR UPDATE). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<MemberDevice> findWithLockByDeviceId(String deviceId);

	Optional<MemberDevice> findByDeviceIdAndMemberId(String deviceId, String memberId);

	List<MemberDevice> findByMemberIdAndFcmTokenIsNotNull(String memberId);

	List<MemberDevice> findByFcmTokenIsNotNull();

	/** 푸시 가능한 기기가 하나라도 있는 회원 ID 집합. 스케줄러가 회원마다 exists 쿼리를 날리는 대신 한 번에 읽는다. */
	@Query("select distinct d.memberId from MemberDevice d where d.fcmToken is not null")
	Set<String> findMemberIdsWithFcmToken();

	/**
	 * 같은 FCM 토큰을 가진 다른 기기 행에서 토큰을 지운다. 푸시 주소 하나는 항상 한 기기(한 회원)에만 붙는다.
	 * 벌크 UPDATE 라 영속성 컨텍스트를 비워 이후 조회가 최신 값을 보게 한다.
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update MemberDevice d set d.fcmToken = null where d.fcmToken = :fcmToken and d.deviceId <> :deviceId")
	int detachFcmTokenFromOthers(@Param("fcmToken") String fcmToken, @Param("deviceId") String deviceId);

	/**
	 * FCM 이 무효라고 응답한 토큰을 정리한다. 트랜잭션 없는 곳(스케줄러, 디버그 API)에서도 호출되므로 자체 트랜잭션을 둔다.
	 * 영속성 컨텍스트를 비우지 않는다 — 호출자(DiarySympathyScheduler)가 같은 트랜잭션에서 다른 엔티티를
	 * 더티체킹으로 저장하는 중일 수 있고, clear 하면 그 변경이 유실된다.
	 */
	@Transactional
	@Modifying
	@Query("update MemberDevice d set d.fcmToken = null where d.fcmToken = :fcmToken")
	int clearFcmToken(@Param("fcmToken") String fcmToken);

	@Transactional
	void deleteByMemberId(String memberId);
}
