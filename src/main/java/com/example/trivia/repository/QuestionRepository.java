package com.example.trivia.repository;

import com.example.trivia.model.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends CrudRepository<Question, String> {
    @Query("SELECT questions.* FROM questions JOIN round_questions ON questions.question_id = round_questions.question_id WHERE round_questions.round_id = :roundId")
    List<Question> findByRoundId(String roundId);

    Page<Question> findByDifficulty(Integer difficulty, Pageable pageable);
}
