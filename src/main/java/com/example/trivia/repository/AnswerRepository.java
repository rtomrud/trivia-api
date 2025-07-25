package com.example.trivia.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.trivia.model.Answer;

@Repository
public interface AnswerRepository extends CrudRepository<Answer, Long> {
    List<Answer> findByRoundIdAndQuestionId(Long roundId, Long questionId);

    Optional<Answer> findByRoundIdAndQuestionIdAndPlayerId(Long roundId, Long questionId, Long playerId);
}
