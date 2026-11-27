package com.momeokji.aiDiarybackend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.momeokji.aiDiarybackend.entity.MemberTermsAgreement;
import com.momeokji.aiDiarybackend.entity.Terms;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.repository.MemberTermsAgreementRepository;
import com.momeokji.aiDiarybackend.repository.SocialAccountRepository;
import com.momeokji.aiDiarybackend.security.RefreshTokens;
import com.momeokji.aiDiarybackend.support.IntegrationTestSupport;
import com.momeokji.aiDiarybackend.support.TestSocialTokens;

/**
 * 가입 시 약관 동의 검증·저장(스펙 2026-11-27 4.3, 4.4). 필수 동의 기록이 없는 회원이 만들어지면 안 된다.
 */
class TermsAgreementIntegrationTest extends IntegrationTestSupport {

	static final String DEVICE = "11111111-1111-1111-1111-111111111111";

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	SocialAccountRepository socialAccountRepository;

	@Autowired
	MemberTermsAgreementRepository agreementRepository;

	@Test
	void signupWithoutTheFieldIsRejectedAndCreatesNothing() throws Exception {
		googleLogin("sub-1", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TERMS_AGREEMENT_REQUIRED"));

		assertNothingCreated();
	}

	@Test
	void signupWithAnEmptyListIsRejected() throws Exception {
		googleLogin("sub-1", List.of())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TERMS_AGREEMENT_REQUIRED"));

		assertNothingCreated();
	}

	/** 필수 세 개 중 AI 국외 이전 동의만 뺀 경우. 선택 항목을 넣어도 대신할 수 없다. */
	@Test
	void signupMissingOneRequiredTermIsRejected() throws Exception {
		List<Long> ids = new ArrayList<>(allTermsIds());
		ids.remove(idOf("AI_OVERSEAS_TRANSFER"));

		googleLogin("sub-1", ids)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TERMS_AGREEMENT_REQUIRED"));

		assertNothingCreated();
	}

	/** 거절이 아무 행도 남기지 않으므로 같은 계정으로 다시 동의하면 가입된다. */
	@Test
	void rejectedSignupCanBeRetriedWithFullConsent() throws Exception {
		googleLogin("sub-1", List.of()).andExpect(status().isBadRequest());

		googleLogin("sub-1", requiredTermsIds())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.isNewMember").value(true));
	}

	@Test
	void signupWithEverythingStoresFourAgreementsAndTurnsNotificationsOn() throws Exception {
		String access = accessToken(googleLogin("sub-1", allTermsIds()).andExpect(status().isOk()));
		String memberId = me(access).get("memberId").asText();

		List<MemberTermsAgreement> agreements = agreementRepository.findByMemberId(memberId);
		assertThat(agreements).extracting(MemberTermsAgreement::getTermsId).containsExactlyInAnyOrderElementsOf(allTermsIds());
		assertThat(agreements).allSatisfy(a -> assertThat(a.getAgreedAt()).isNotNull());
		assertThat(me(access).get("notificationEnabled").asBoolean()).isTrue();
	}

	@Test
	void signupWithRequiredOnlyStoresThreeAgreementsAndLeavesNotificationsOff() throws Exception {
		String access = accessToken(googleLogin("sub-1", requiredTermsIds()).andExpect(status().isOk()));
		String memberId = me(access).get("memberId").asText();

		assertThat(agreementRepository.findByMemberId(memberId))
			.extracting(MemberTermsAgreement::getTermsId).containsExactlyInAnyOrderElementsOf(requiredTermsIds());
		assertThat(me(access).get("notificationEnabled").asBoolean()).isFalse();
	}

	/** 앱이 목록을 받은 뒤 약관이 바뀌었거나 값이 깨져 와도 500 이 아니라 필수 여부만 본다. */
	@Test
	void unknownNullAndDuplicateIdsAreIgnored() throws Exception {
		List<Long> ids = new ArrayList<>(requiredTermsIds());
		ids.add(null);
		ids.add(999_999L);
		ids.add(requiredTermsIds().get(0));

		String access = accessToken(googleLogin("sub-1", ids).andExpect(status().isOk()));

		assertThat(agreementRepository.findByMemberId(me(access).get("memberId").asText())).hasSize(3);
	}

	/** 꺼진 옛 버전에 대한 동의는 새 버전의 동의가 아니다. */
	@Test
	void consentToASupersededVersionDoesNotCount() throws Exception {
		List<Long> staleIds = requiredTermsIds();
		Terms old = termsRepository.findById(idOf("AI_OVERSEAS_TRANSFER")).orElseThrow();
		old.deactivate();
		termsRepository.save(old);
		termsRepository.save(Terms.create("AI_OVERSEAS_TRANSFER", 2, "AI 국외 이전 동의(개정)", true, 3, "/ai-consent",
			java.time.LocalDateTime.now()));

		googleLogin("sub-1", staleIds)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TERMS_AGREEMENT_REQUIRED"));

		assertNothingCreated();
	}

	/** 초기 데이터를 넣지 않은 채 배포하면 검증이 빈 조건으로 통과한다. 그 경우 가입을 막는다. */
	@Test
	void signupIsRefusedWhenNoRequiredTermsAreConfigured() throws Exception {
		termsRepository.deleteAll();

		googleLogin("sub-1", List.of())
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("TERMS_NOT_CONFIGURED"));

		assertNothingCreated();
	}

	/** 기존 회원의 로그인은 약관을 보지 않는다. 필드가 없어도 되고, 보낸 값으로 기록이나 알림 설정이 바뀌지 않는다. */
	@Test
	void existingMemberLoginIgnoresTheField() throws Exception {
		String access = accessToken(googleLogin("sub-1", requiredTermsIds()).andExpect(status().isOk()));
		String memberId = me(access).get("memberId").asText();

		googleLogin("sub-1", null).andExpect(status().isOk()).andExpect(jsonPath("$.isNewMember").value(false));
		String again = accessToken(googleLogin("sub-1", allTermsIds()).andExpect(status().isOk()));

		assertThat(agreementRepository.findByMemberId(memberId)).hasSize(3);
		assertThat(me(again).get("notificationEnabled").asBoolean()).isFalse();
	}

	/** 약관이 전부 사라져도 이미 가입한 회원은 로그인된다. */
	@Test
	void existingMemberCanLogInEvenWhenTermsAreMissing() throws Exception {
		googleLogin("sub-1", requiredTermsIds()).andExpect(status().isOk());
		termsRepository.deleteAll();

		googleLogin("sub-1", null).andExpect(status().isOk());
	}

	@Test
	void appleSignupGoesThroughTheSameCheck() throws Exception {
		appleLogin("sub-a", null)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TERMS_AGREEMENT_REQUIRED"));
		assertNothingCreated();

		String access = accessToken(appleLogin("sub-a", allTermsIds()).andExpect(status().isOk()));

		assertThat(agreementRepository.findByMemberId(me(access).get("memberId").asText())).hasSize(4);
	}

	/** 방침 4항: 탈퇴 후에는 회원 번호와 가입·탈퇴 일시만 남는다. */
	@Test
	void withdrawRemovesAgreements() throws Exception {
		String access = accessToken(googleLogin("sub-1", allTermsIds()).andExpect(status().isOk()));
		String other = accessToken(googleLogin("sub-2", requiredTermsIds(), "22222222-2222-2222-2222-222222222222")
			.andExpect(status().isOk()));
		String memberId = me(access).get("memberId").asText();
		String otherId = me(other).get("memberId").asText();

		mockMvc.perform(delete("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
			.andExpect(status().isNoContent());

		assertThat(agreementRepository.findByMemberId(memberId)).isEmpty();
		assertThat(agreementRepository.findByMemberId(otherId)).hasSize(3);
	}

	// ---- helpers ----

	private void assertNothingCreated() {
		assertThat(memberRepository.count()).isZero();
		assertThat(socialAccountRepository.count()).isZero();
		assertThat(agreementRepository.count()).isZero();
	}

	private Long idOf(String code) {
		return termsRepository.findByIsActiveTrueOrderByDisplayOrderAsc().stream()
			.filter(t -> t.getCode().equals(code)).findFirst().orElseThrow().getId();
	}

	private ResultActions googleLogin(String sub, List<Long> agreedTermsIds) throws Exception {
		return googleLogin(sub, agreedTermsIds, DEVICE);
	}

	private ResultActions googleLogin(String sub, List<Long> agreedTermsIds, String deviceId) throws Exception {
		Map<String, Object> req = new HashMap<>();
		req.put("idToken", TestSocialTokens.google(sub, "ios-client", sub + "@gmail.com"));
		req.put("deviceId", deviceId);
		req.put("os", "IOS");
		if (agreedTermsIds != null) {
			req.put("agreedTermsIds", agreedTermsIds);
		}
		return mockMvc.perform(post("/auth/social/google").contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(req)));
	}

	private ResultActions appleLogin(String sub, List<Long> agreedTermsIds) throws Exception {
		Map<String, Object> req = new HashMap<>();
		req.put("identityToken", TestSocialTokens.apple(sub, "com.momeokji.cake", RefreshTokens.sha256Hex("n"), null));
		req.put("authorizationCode", "code-1");
		req.put("nonce", "n");
		req.put("deviceId", DEVICE);
		req.put("os", "IOS");
		if (agreedTermsIds != null) {
			req.put("agreedTermsIds", agreedTermsIds);
		}
		return mockMvc.perform(post("/auth/social/apple").contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(req)));
	}

	private String accessToken(ResultActions login) throws Exception {
		return objectMapper.readTree(login.andReturn().getResponse().getContentAsString()).get("accessToken").asText();
	}

	private JsonNode me(String accessToken) throws Exception {
		return objectMapper.readTree(mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
	}
}
