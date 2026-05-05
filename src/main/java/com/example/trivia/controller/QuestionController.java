package com.example.trivia.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.trivia.model.Question;
import com.example.trivia.model.Round;
import com.example.trivia.repository.QuestionRepository;
import com.example.trivia.repository.RoundRepository;

@RestController
public class QuestionController {
    private final QuestionRepository questionRepo;
    private final RoundRepository roundRepo;

    public QuestionController(QuestionRepository questionRepo, RoundRepository roundRepo) {
        this.questionRepo = questionRepo;
        this.roundRepo = roundRepo;
    }

    @GetMapping("/questions")
    public ResponseEntity<List<Question>> getQuestions(@RequestParam Long roundId) {
        Round round = roundRepo.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (Instant.now().isBefore(round.getCreatedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round has not started yet");
        }

        List<Question> questions = questionRepo.findByRoundId(roundId);

        if (Instant.now().isBefore(round.getEndedAt())) {
            for (Question question : questions) {
                question.setCorrectAnswers(new ArrayList<>());
            }
        }

        return ResponseEntity.ok(questions);
    }
}
