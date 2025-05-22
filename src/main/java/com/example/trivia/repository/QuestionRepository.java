package com.example.trivia.repository;

import com.example.trivia.model.Question;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface QuestionRepository extends CrudRepository<Question, String> {
	List<Question> findByRoundId(String roundId);
}
