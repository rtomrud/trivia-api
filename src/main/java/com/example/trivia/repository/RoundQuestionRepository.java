package com.example.trivia.repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.trivia.model.RoundQuestion;
import com.example.trivia.model.RoundQuestionId;

@Repository
public interface RoundQuestionRepository extends CrudRepository<RoundQuestion, RoundQuestionId> {
    Optional<RoundQuestion> findById(RoundQuestionId id);
}
