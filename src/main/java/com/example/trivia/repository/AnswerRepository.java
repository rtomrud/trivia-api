package com.example.trivia.repository;

import com.example.trivia.model.Answer;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnswerRepository extends CrudRepository<Answer, String> {
    Optional<Answer> findByRoundIdAndQuestionIdAndPlayerId(
            String roundId,
            String questionId,
            String playerId);
}
