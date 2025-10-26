package com.momeokji.aiDiarybackend.repository;

import com.momeokji.aiDiarybackend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Integer> {

	@Query(value = """
        SELECT * FROM question_list
        WHERE question_id NOT IN (1,2)
        ORDER BY RAND()
        LIMIT :limit
        """, nativeQuery = true)
	List<Question> pickRandomQuestion(@Param("limit") int limit);
}
