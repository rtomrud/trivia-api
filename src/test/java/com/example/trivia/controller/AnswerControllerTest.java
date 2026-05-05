package com.example.trivia.controller;

import com.example.trivia.dto.AnswerSubmissionRequest;
import com.example.trivia.model.Answer;
import com.example.trivia.model.Game;
import com.example.trivia.model.Player;
import com.example.trivia.model.Question;
import com.example.trivia.model.Round;
import com.example.trivia.repository.AnswerRepository;
import com.example.trivia.repository.GameRepository;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.QuestionRepository;
import com.example.trivia.repository.RoundRepository;
import com.example.trivia.service.SseService;

import jakarta.servlet.http.HttpServletRequest;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerControllerTest {
    @Mock
    private AnswerRepository answerRepo;

    @Mock
    private GameRepository gameRepo;

    @Mock
    private PlayerRepository playerRepo;

    @Mock
    private QuestionRepository questionRepo;

    @Mock
    private RoundRepository roundRepo;

    @Mock
    private SseService sseService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AnswerController answerController;

    private Answer testAnswer;
    private Game testGame;
    private Player testPlayer;
    private Question testQuestion;
    private Round testRound;

    @BeforeEach
    void setUp() {
        testQuestion = new Question();
        testQuestion.setId(1L);

        testGame = new Game();
        testGame.setId(1L);
        testGame.setRoomId(1L);

        testRound = new Round();
        testRound.setId(1L);
        testRound.setGameId(1L);
        testRound.setCreatedAt(Instant.now());
        testRound.setEndedAt(Instant.now().plus(Duration.ofMinutes(10)));
        testRound.addQuestion(testQuestion);

        testPlayer = new Player();
        testPlayer.setId(1L);
        testPlayer.setRoomId(1L);
        testPlayer.setTeamId(1L);

        testAnswer = new Answer();
        testAnswer.setId(1L);
        testAnswer.setRoundId(1L);
        testAnswer.setQuestionId(1L);
        testAnswer.setPlayerId(1L);
        testAnswer.setTeamId(1L);
        testAnswer.setAnswer("my answer");
    }

    @Test
    void submitAnswer_submitsAnswerAndReturns200() {
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(request.getAttribute("playerId")).thenReturn(1L);
        when(playerRepo.findById(1L)).thenReturn(Optional.of(testPlayer));
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(answerRepo.findByRoundIdAndQuestionIdAndPlayerId(1L, 1L, 1L)).thenReturn(Optional.empty());
        when(answerRepo.save(any(Answer.class))).thenReturn(testAnswer);

        ResponseEntity<Answer> response = answerController.submitAnswer(
                1L, 1L, new AnswerSubmissionRequest("my answer"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testAnswer, response.getBody());
        verify(answerRepo).save(any(Answer.class));
    }

    @Test
    void submitAnswer_throws401WhenNotAuthenticated() {
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> answerController.submitAnswer(1L, 1L, new AnswerSubmissionRequest("answer"), request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void submitAnswer_throws404WhenQuestionNotInRound() {
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> answerController.submitAnswer(1L, 99L, new AnswerSubmissionRequest("answer"), request));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void submitAnswer_throws403WhenRoundEnded() {
        testRound.setEndedAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(request.getAttribute("playerId")).thenReturn(1L);
        when(playerRepo.findById(1L)).thenReturn(Optional.of(testPlayer));
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> answerController.submitAnswer(1L, 1L, new AnswerSubmissionRequest("answer"), request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void submitAnswer_throws403WhenPlayerNotInTeam() {
        testPlayer.setTeamId(null);
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(request.getAttribute("playerId")).thenReturn(1L);
        when(playerRepo.findById(1L)).thenReturn(Optional.of(testPlayer));
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> answerController.submitAnswer(1L, 1L, new AnswerSubmissionRequest("answer"), request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getAnswers_returnsAnswersWhenRoundEnded() {
        testRound.setEndedAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(questionRepo.findById(1L)).thenReturn(Optional.of(testQuestion));
        when(answerRepo.findByRoundIdAndQuestionId(1L, 1L)).thenReturn(List.of(testAnswer));

        ResponseEntity<List<Answer>> response = answerController.getAnswers(1L, 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(answerRepo).findByRoundIdAndQuestionId(1L, 1L);
    }

    @Test
    void getAnswers_throws403WhenRoundNotEnded() {
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(questionRepo.findById(1L)).thenReturn(Optional.of(testQuestion));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> answerController.getAnswers(1L, 1L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void getAnswer_returnsOwnAnswer() {
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(request.getAttribute("playerId")).thenReturn(1L);
        when(playerRepo.findById(1L)).thenReturn(Optional.of(testPlayer));
        when(answerRepo.findByRoundIdAndQuestionIdAndPlayerId(1L, 1L, 1L)).thenReturn(Optional.of(testAnswer));

        ResponseEntity<Answer> response = answerController.getAnswer(1L, 1L, 1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testAnswer, response.getBody());
    }

    @Test
    void getAnswer_throws401WhenNotAuthenticated() {
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> answerController.getAnswer(1L, 1L, 1L, request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void getAnswer_throws403WhenViewingAnotherTeamAnswer() {
        Player otherPlayer = new Player();
        otherPlayer.setId(2L);
        otherPlayer.setRoomId(1L);
        otherPlayer.setTeamId(2L);

        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(request.getAttribute("playerId")).thenReturn(1L);
        when(playerRepo.findById(1L)).thenReturn(Optional.of(testPlayer));
        when(playerRepo.findById(2L)).thenReturn(Optional.of(otherPlayer));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> answerController.getAnswer(1L, 1L, 2L, request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
