package com.example.trivia.repository;

import com.example.trivia.model.RoundQuestion;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundQuestionRepository extends CrudRepository<RoundQuestion, Long> {
    Optional<RoundQuestion> findByRoundIdAndQuestionId(Long roundId, Long questionId);

    List<RoundQuestion> findByRoundId(Long roundId);

    List<RoundQuestion> findByQuestionId(Long questionId);
}
