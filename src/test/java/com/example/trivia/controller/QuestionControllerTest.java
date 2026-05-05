package com.example.trivia.controller;

import com.example.trivia.model.Question;
import com.example.trivia.model.Round;
import com.example.trivia.repository.QuestionRepository;
import com.example.trivia.repository.RoundRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {
    @Mock
    private QuestionRepository questionRepo;

    @Mock
    private RoundRepository roundRepo;

    @InjectMocks
    private QuestionController questionController;

    private Question testQuestion;
    private Round testRound;

    @BeforeEach
    void setUp() {
        testQuestion = new Question();
        testQuestion.setId(1L);
        testQuestion.setType("multiple_choice");
        testQuestion.setQuestion("Test question");

        testRound = new Round();
        testRound.setId(1L);
        testRound.setGameId(1L);
        testRound.setCreatedAt(Instant.now());
        testRound.setEndedAt(Instant.now().plus(Duration.ofMinutes(10)));
    }

    @Test
    void getQuestions_returnsQuestionsList() {
        testQuestion.setCorrectAnswers(List.of("answer1"));
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(questionRepo.findByRoundId(1L)).thenReturn(List.of(testQuestion));

        ResponseEntity<List<Question>> response = questionController.getQuestions(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertTrue(response.getBody().get(0).getCorrectAnswers().isEmpty());
        verify(questionRepo).findByRoundId(1L);
    }

    @Test
    void getQuestions_returnsCorrectAnswersWhenRoundEnded() {
        testRound.setEndedAt(Instant.now().minus(Duration.ofMinutes(1)));
        testQuestion.setCorrectAnswers(List.of("answer1"));
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(questionRepo.findByRoundId(1L)).thenReturn(List.of(testQuestion));

        ResponseEntity<List<Question>> response = questionController.getQuestions(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of("answer1"), response.getBody().get(0).getCorrectAnswers());
        verify(questionRepo).findByRoundId(1L);
    }

    @Test
    void getQuestions_throws403WhenRoundNotStarted() {
        Round round = new Round();
        round.setId(1L);
        round.setGameId(1L);
        round.setCreatedAt(Instant.now().plus(Duration.ofMinutes(10)));

        when(roundRepo.findById(1L)).thenReturn(Optional.of(round));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> questionController.getQuestions(1L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(roundRepo).findById(1L);
    }
}
