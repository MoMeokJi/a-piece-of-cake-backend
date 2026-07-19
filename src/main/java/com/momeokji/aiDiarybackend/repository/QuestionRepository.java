package com.momeokji.aiDiarybackend.repository;

import com.momeokji.aiDiarybackend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Integer> {

	@Query(value = """
        SELECT * FROM question_list
		WHERE UPPER(category) = UPPER(:category)
        ORDER BY RAND()
        LIMIT :limit
        """, nativeQuery = true)
	List<Question> pickRandomQuestionByCategory(@Param("category") String category, @Param("limit") int limit);
}
