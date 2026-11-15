package com.momeokji.aiDiarybackend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.momeokji.aiDiarybackend.entity.MemberDevice;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberDeviceRepositoryTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);
	private static final LocalDateTime EXPIRES = NOW.plusDays(60);

	@Autowired
	MemberDeviceRepository repository;

	@Autowired
	TestEntityManager em;

	@Test
	void fcmTokenIsUnique() {
		repository.save(MemberDevice.open("dev-a", "m1", "IOS", "fcm-X", "h1", EXPIRES, NOW));
		repository.save(MemberDevice.open("dev-b", "m2", "AND", "fcm-X", "h2", EXPIRES, NOW));

		assertThatThrownBy(() -> em.flush())
			.isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
	}

	@Test
	void detachFcmTokenFromOthersClearsOnlyOtherRows() {
		repository.save(MemberDevice.open("dev-a", "m1", "IOS", "fcm-X", "h1", EXPIRES, NOW));
		repository.save(MemberDevice.open("dev-b", "m2", "AND", null, "h2", EXPIRES, NOW));
		em.flush();

		int updated = repository.detachFcmTokenFromOthers("fcm-X", "dev-b");

		assertThat(updated).isEqualTo(1);
		assertThat(repository.findById("dev-a").orElseThrow().getFcmToken()).isNull();
		assertThat(repository.findById("dev-b").orElseThrow().getFcmToken()).isNull();
	}

	@Test
	void reassignToOtherMemberWithoutFcmTokenClearsIt() {
		MemberDevice device = MemberDevice.open("dev-a", "m1", "IOS", "fcm-X", "h1", EXPIRES, NOW);
		device.rotate("h1b", EXPIRES, NOW);

		device.reassign("m2", "AND", null, "h2", EXPIRES.plusDays(1), NOW.plusHours(1));

		assertThat(device.getMemberId()).isEqualTo("m2");
		assertThat(device.getOs()).isEqualTo("AND");
		assertThat(device.getFcmToken()).isNull();
		assertThat(device.getRefreshTokenHash()).isEqualTo("h2");
		assertThat(device.getPrevRefreshTokenHash()).isNull();
		assertThat(device.getRotatedAt()).isNull();
		assertThat(device.getRefreshExpiresAt()).isEqualTo(EXPIRES.plusDays(1));
		assertThat(device.getLastUsedAt()).isEqualTo(NOW.plusHours(1));
	}

	@Test
	void sameMemberReloginWithoutFcmTokenKeepsIt() {
		MemberDevice device = MemberDevice.open("dev-a", "m1", "IOS", "fcm-X", "h1", EXPIRES, NOW);

		device.reassign("m1", "IOS", null, "h2", EXPIRES, NOW);

		assertThat(device.getFcmToken()).isEqualTo("fcm-X");
		assertThat(device.getRefreshTokenHash()).isEqualTo("h2");
		assertThat(device.getPrevRefreshTokenHash()).isNull();
	}

	@Test
	void rotateMovesCurrentHashToPrev() {
		MemberDevice device = MemberDevice.open("dev-a", "m1", "IOS", "fcm-X", "h1", EXPIRES, NOW);
		LocalDateTime later = NOW.plusMinutes(5);

		device.rotate("h2", later.plusDays(60), later);

		assertThat(device.getRefreshTokenHash()).isEqualTo("h2");
		assertThat(device.getPrevRefreshTokenHash()).isEqualTo("h1");
		assertThat(device.getRotatedAt()).isEqualTo(later);
		assertThat(device.getLastUsedAt()).isEqualTo(later);
		assertThat(device.getRefreshExpiresAt()).isEqualTo(later.plusDays(60));
	}

	@Test
	void memberIdsWithFcmTokenAreDistinctAndExcludeTokenlessMembers() {
		repository.save(MemberDevice.open("dev-a", "m1", "IOS", null, "h1", EXPIRES, NOW));
		repository.save(MemberDevice.open("dev-b", "m2", "IOS", "fcm-Y", "h2", EXPIRES, NOW));
		repository.save(MemberDevice.open("dev-c", "m2", "AND", "fcm-Z", "h3", EXPIRES, NOW));
		repository.save(MemberDevice.open("dev-d", "m3", "AND", "fcm-W", "h4", EXPIRES, NOW));
		em.flush();

		assertThat(repository.findMemberIdsWithFcmToken()).containsExactlyInAnyOrder("m2", "m3");
	}
}
