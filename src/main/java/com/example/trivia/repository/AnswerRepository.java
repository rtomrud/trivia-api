package com.example.trivia.repository;

import com.example.trivia.model.Answer;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnswerRepository extends CrudRepository<Answer, Long> {
    List<Answer> findByRoundIdAndQuestionId(Long roundId, Long questionId);
}
