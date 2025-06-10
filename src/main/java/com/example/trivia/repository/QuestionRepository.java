package com.example.trivia.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.trivia.model.Question;

@Repository
public interface QuestionRepository extends CrudRepository<Question, Long> {
    Page<Question> findAll(Pageable pageable);

    @Query("SELECT questions.* FROM questions JOIN round_questions ON questions.question_id = round_questions.question_id WHERE round_questions.round_id = :roundId")
    List<Question> findByRoundId(Long roundId);
}
