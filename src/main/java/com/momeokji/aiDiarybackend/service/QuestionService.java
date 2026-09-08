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
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionService {

	private static final String REQUIRED_CATEGORY = "REQUIRED";
	private static final String OPTIONAL_CATEGORY = "OPTIONAL";
	private static final int REQUIRED_QUESTION_COUNT = 2;
	private static final int OPTIONAL_QUESTION_COUNT = 3;

	private final QuestionRepository questionRepository;

	@Transactional(readOnly = true)
	public DailyQuestionsResponseDto getQuestion() {
		List<Question> requiredQuestions = questionRepository.pickRandomQuestionByCategory(REQUIRED_CATEGORY, REQUIRED_QUESTION_COUNT);
		List<Question> optionalQuestions = questionRepository.pickRandomQuestionByCategory(OPTIONAL_CATEGORY, OPTIONAL_QUESTION_COUNT);

		if (requiredQuestions.size() < REQUIRED_QUESTION_COUNT || optionalQuestions.size() < OPTIONAL_QUESTION_COUNT) {
			throw new IllegalStateException("질문 개수가 부족합니다. REQUIRED 2개 이상, OPTIONAL 3개 이상 필요합니다.");
		}

		List<String> questions = new ArrayList<>(5);
		for (Question q : requiredQuestions) {
			questions.add(q.getContent());
		}
		for (Question q : optionalQuestions) {
			questions.add(q.getContent());
		}

		return DailyQuestionsResponseDto.builder()
			.questions(questions)
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

	private String validateAndNormalizeCategory(String category) {
		String normalized = category.toUpperCase();
		if (!REQUIRED_CATEGORY.equals(normalized) && !OPTIONAL_CATEGORY.equals(normalized)) {
			throw new IllegalArgumentException("category는 REQUIRED 또는 OPTIONAL만 가능합니다.");
		}
		return normalized;
	}

}
