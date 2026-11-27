package com.momeokji.aiDiarybackend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.momeokji.aiDiarybackend.entity.Member;

/** 알림 토글 관련 파생 쿼리가 실제 DB 에서 켜진 회원만 고르는지. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberRepositoryTest {

	@Autowired
	MemberRepository memberRepository;

	@Test
	void remindTargetsExcludeDefaultOffMembers() {
		Member on = Member.create("on", false);
		on.changeNotificationEnabled(true);
		memberRepository.save(on);
		memberRepository.save(Member.create("off-by-default", false));
		Member withdrawn = Member.create("withdrawn-on", false);
		withdrawn.changeNotificationEnabled(true);
		memberRepository.save(withdrawn);
		memberRepository.softDeleteOne("withdrawn-on", java.time.LocalDateTime.now());

		assertThat(memberRepository.findByIsValidTrueAndNotificationEnabledTrue())
			.extracting(Member::getMemberId)
			.containsExactly("on");
	}

	@Test
	void batchLookupReturnsOnlyValidRequestedMembers() {
		memberRepository.save(Member.create("m1", false));
		memberRepository.save(Member.create("m2", false));
		memberRepository.save(Member.create("m3", false));
		memberRepository.softDeleteOne("m2", java.time.LocalDateTime.now());

		assertThat(memberRepository.findByMemberIdInAndIsValidTrue(Set.of("m1", "m2", "m3", "ghost")))
			.extracting(Member::getMemberId)
			.containsExactlyInAnyOrder("m1", "m3");
	}
}
