package com.momeokji.aiDiarybackend.service;

import com.momeokji.aiDiarybackend.dto.response.DailyQuestionsResponseDto;
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

	private final QuestionRepository questionRepository;

	@Transactional(readOnly = true)
	public DailyQuestionsResponseDto getQuestion() {
		Question q1 = questionRepository.findById(1).orElseThrow();
		Question q2 = questionRepository.findById(2).orElseThrow();
		List<Question> randomQuestions = questionRepository.pickRandomQuestion(3);

		List<String> questions = new ArrayList<>(5);
		questions.add(q1.getContent());
		questions.add(q2.getContent());
		for (Question q : randomQuestions) {
			questions.add(q.getContent());
		}

		return DailyQuestionsResponseDto.builder()
			.questions(questions)
			.build();
	}

}
