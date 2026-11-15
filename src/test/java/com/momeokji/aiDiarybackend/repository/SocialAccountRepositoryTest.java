package com.momeokji.aiDiarybackend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.momeokji.aiDiarybackend.entity.SocialAccount;
import com.momeokji.aiDiarybackend.entity.SocialProvider;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SocialAccountRepositoryTest {

	@Autowired
	SocialAccountRepository repository;

	@Autowired
	TestEntityManager em;

	@Test
	void providerAndProviderUserIdIsUnique() {
		repository.save(SocialAccount.link("m1", SocialProvider.GOOGLE, "sub-1", "a@x.com"));

		// IDENTITY 키라 save 시점에 INSERT 가 나간다. 두 번째 save 와 flush 를 함께 감싼다.
		assertThatThrownBy(() -> {
			repository.save(SocialAccount.link("m2", SocialProvider.GOOGLE, "sub-1", null));
			em.flush();
		})
			.isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
	}

	@Test
	void sameProviderUserIdOnOtherProviderIsAllowed() {
		repository.save(SocialAccount.link("m1", SocialProvider.GOOGLE, "sub-1", null));
		repository.save(SocialAccount.link("m2", SocialProvider.APPLE, "sub-1", null));
		em.flush();

		assertThat(repository.findByProviderAndProviderUserId(SocialProvider.APPLE, "sub-1"))
			.get().extracting(SocialAccount::getMemberId).isEqualTo("m2");
	}

	@Test
	void updateEmailIfPresentIgnoresNull() {
		SocialAccount account = SocialAccount.link("m1", SocialProvider.APPLE, "sub-a", "a@x.com");

		account.updateEmailIfPresent(null);
		assertThat(account.getEmail()).isEqualTo("a@x.com");

		account.updateEmailIfPresent("  ");
		assertThat(account.getEmail()).isEqualTo("a@x.com");

		account.updateEmailIfPresent("b@x.com");
		assertThat(account.getEmail()).isEqualTo("b@x.com");
	}
}
