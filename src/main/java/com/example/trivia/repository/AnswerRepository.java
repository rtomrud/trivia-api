package com.example.trivia.repository;

import com.example.trivia.model.Answer;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends CrudRepository<Answer, String> {
	Optional<Answer> findByQuestionIdAndPlayerId(String questionId, String playerId);

	List<Answer> findByQuestionId(String questionId);
}
