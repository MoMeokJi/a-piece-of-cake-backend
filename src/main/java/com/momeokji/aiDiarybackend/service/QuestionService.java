package com.momeokji.aiDiarybackend.service;

import com.momeokji.aiDiarybackend.dto.request.QuestionCreateRequestDto;
import com.momeokji.aiDiarybackend.dto.request.QuestionUpdateRequestDto;
import com.momeokji.aiDiarybackend.dto.response.DailyQuestionsResponseDto;
import com.momeokji.aiDiarybackend.dto.response.QuestionResponseDto;
import com.momeokji.aiDiarybackend.entity.Question;
import com.momeokji.aiDiarybackend.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestionService {

	private static final String REQUIRED_CATEGORY = "REQUIRED";
	private static final String OPTIONAL_CATEGORY = "OPTIONAL";
	private static final int REQUIRED_QUESTION_COUNT = 2;
	private static final int OPTIONAL_QUESTION_COUNT = 3;
	/** 선택 질문을 건너뛸 때 바꿔 끼울 여분의 최대 개수. */
	private static final int OPTIONAL_SPARE_MAX = 5;

	private final QuestionRepository questionRepository;

	/**
	 * 오늘의 문답 질문. 필수 2개 + 선택 3개에 더해, 선택 질문을 건너뛸 때 바꿔 끼울 여분을 준다.
	 * 필수 질문은 건너뛸 수 없으므로 여분이 없다. 여분은 DB 에 남은 만큼만(최대 {@value #OPTIONAL_SPARE_MAX}개) 준다.
	 */
	@Transactional(readOnly = true)
	public DailyQuestionsResponseDto getQuestion() {
		List<Question> requiredQuestions = questionRepository.pickRandomQuestionByCategory(REQUIRED_CATEGORY, REQUIRED_QUESTION_COUNT);
		List<Question> optionalQuestions = questionRepository.pickRandomQuestionByCategory(
			OPTIONAL_CATEGORY, OPTIONAL_QUESTION_COUNT + OPTIONAL_SPARE_MAX);

		// 같은 문구가 여러 행에 들어 있어도 한 화면에는 한 번만 보이게 문구 기준으로 중복을 뺀다.
		Set<String> required = distinctContents(requiredQuestions, Set.of());
		Set<String> optional = distinctContents(optionalQuestions, required);

		if (required.size() < REQUIRED_QUESTION_COUNT || optional.size() < OPTIONAL_QUESTION_COUNT) {
			throw new IllegalStateException("질문 개수가 부족합니다. REQUIRED 2개 이상, OPTIONAL 3개 이상 필요합니다.");
		}

		List<String> optionalList = List.copyOf(optional);
		List<String> questions = new ArrayList<>(REQUIRED_QUESTION_COUNT + OPTIONAL_QUESTION_COUNT);
		questions.addAll(required);
		questions.addAll(optionalList.subList(0, OPTIONAL_QUESTION_COUNT));
		List<String> optionalSpares = optionalList.subList(OPTIONAL_QUESTION_COUNT, optionalList.size());

		return DailyQuestionsResponseDto.builder()
			.questions(questions)
			.optionalSpares(optionalSpares)
			.build();
	}

	@Transactional
	public QuestionResponseDto createQuestion(QuestionCreateRequestDto req) {
		Question question = questionRepository.save(
			Question.builder()
				.content(req.getContent())
				.category(validateAndNormalizeCategory(req.getCategory()))
				.build()
		);

		return QuestionResponseDto.from(question);
	}

	@Transactional
	public QuestionResponseDto updateQuestion(
		Integer questionId,
		QuestionUpdateRequestDto req
	) {
		Question question = questionRepository.findById(questionId)
			.orElseThrow(() -> new IllegalArgumentException(
				"존재하지 않는 질문입니다. id=" + questionId
			));

		question.update(
			req.getContent(),
			validateAndNormalizeCategory(req.getCategory())
		);

		return QuestionResponseDto.from(question);
	}

	@Transactional
	public void deleteQuestion(Integer id) {
		if (!questionRepository.existsById(id)) {
			throw new IllegalArgumentException("존재하지 않는 질문입니다. id=" + id);
		}
		questionRepository.deleteById(id);
	}

	@Transactional(readOnly = true)
	public List<QuestionResponseDto> getQuestions() {
		return questionRepository.findAll().stream()
			.map(QuestionResponseDto::from)
			.toList();
	}

	/** 뽑힌 순서를 유지하면서 문구가 비어 있는 행, 문구가 같은 질문, {@code exclude} 에 이미 있는 문구를 뺀다. */
	private static Set<String> distinctContents(List<Question> picked, Set<String> exclude) {
		Set<String> contents = new LinkedHashSet<>();
		for (Question q : picked) {
			String content = q.getContent();
			if (content == null || content.isBlank() || exclude.contains(content)) {
				continue;
			}
			contents.add(content);
		}
		return contents;
	}

	private String validateAndNormalizeCategory(String category) {
		String normalized = category.toUpperCase();
		if (!REQUIRED_CATEGORY.equals(normalized) && !OPTIONAL_CATEGORY.equals(normalized)) {
			throw new IllegalArgumentException("category는 REQUIRED 또는 OPTIONAL만 가능합니다.");
		}
		return normalized;
	}

}
