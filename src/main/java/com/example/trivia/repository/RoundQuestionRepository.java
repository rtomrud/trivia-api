package com.example.trivia.repository;

import com.example.trivia.model.RoundQuestion;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoundQuestionRepository extends CrudRepository<RoundQuestion, String> {
    Optional<RoundQuestion> findByRoundIdAndQuestionId(String roundId, String questionId);

    List<RoundQuestion> findByRoundId(String roundId);

    List<RoundQuestion> findByQuestionId(String questionId);
}
