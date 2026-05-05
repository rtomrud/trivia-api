package com.example.trivia.controller;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.trivia.dto.AnswerSubmissionRequest;
import com.example.trivia.model.Answer;
import com.example.trivia.model.Game;
import com.example.trivia.model.Player;
import com.example.trivia.model.QuestionRef;
import com.example.trivia.model.Round;
import com.example.trivia.repository.AnswerRepository;
import com.example.trivia.repository.GameRepository;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.QuestionRepository;
import com.example.trivia.repository.RoundRepository;
import com.example.trivia.service.SseService;

@RestController
public class AnswerController {
    private final AnswerRepository answerRepo;
    private final GameRepository gameRepo;
    private final PlayerRepository playerRepo;
    private final QuestionRepository questionRepo;
    private final RoundRepository roundRepo;
    private final SseService sseService;

    public AnswerController(
            AnswerRepository answerRepo,
            GameRepository gameRepo,
            PlayerRepository playerRepo,
            QuestionRepository questionRepo,
            RoundRepository roundRepo,
            SseService sseService) {
        this.answerRepo = answerRepo;
        this.gameRepo = gameRepo;
        this.playerRepo = playerRepo;
        this.questionRepo = questionRepo;
        this.roundRepo = roundRepo;
        this.sseService = sseService;
    }

    @PostMapping("/answers")
    public ResponseEntity<Answer> submitAnswer(
            @RequestParam Long roundId,
            @RequestParam Long questionId,
            @RequestBody AnswerSubmissionRequest body,
            HttpServletRequest request) {
        Round round = roundRepo.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (!round.getQuestions().contains(new QuestionRef(questionId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
        }

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player currentPlayer = playerRepo.findById(currentPlayerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Player is not in the room"));

        Game game = gameRepo.findById(round.getGameId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        if (!currentPlayer.getRoomId().equals(game.getRoomId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player is not in the room");
        }

        if (Instant.now().isAfter(round.getEndedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round has already ended");
        }

        if (currentPlayer.getTeamId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not in a team");
        }

        Answer answer = answerRepo.findByRoundIdAndQuestionIdAndPlayerId(roundId, questionId, currentPlayerId)
                .orElse(new Answer());
        answer.setRoundId(roundId);
        answer.setQuestionId(questionId);
        answer.setPlayerId(currentPlayer.getId());
        answer.setTeamId(currentPlayer.getTeamId());
        answer.setAnswer(body.answer());
        answer.setCreatedAt(Instant.now());
        answer = answerRepo.save(answer);
        sseService.publish(game.getRoomId().toString(), "player-submitted-answer", currentPlayerId);
        return ResponseEntity.ok(answer);
    }

    @GetMapping(value = "/answers", params = {"roundId", "questionId", "!playerId"})
    public ResponseEntity<List<Answer>> getAnswers(
            @RequestParam Long roundId,
            @RequestParam Long questionId) {
        Round round = roundRepo.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        questionRepo.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        if (Instant.now().isBefore(round.getEndedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round has not ended yet");
        }

        List<Answer> answers = answerRepo.findByRoundIdAndQuestionId(roundId, questionId);
        return ResponseEntity.ok(answers);
    }

    @GetMapping(value = "/answers", params = {"roundId", "questionId", "playerId"})
    public ResponseEntity<Answer> getAnswer(
            @RequestParam Long roundId,
            @RequestParam Long questionId,
            @RequestParam Long playerId,
            HttpServletRequest request) {
        Round round = roundRepo.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (!round.getQuestions().contains(new QuestionRef(questionId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
        }

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player currentPlayer = playerRepo.findById(currentPlayerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not in the game"));

        if (currentPlayer.getTeamId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player not in a team");
        }

        if (!currentPlayerId.equals(playerId)) {
            Player player = playerRepo.findById(playerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));

            if (!currentPlayer.getTeamId().equals(player.getTeamId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot view an answer from a player in another team");
            }
        }

        Answer answer = answerRepo.findByRoundIdAndQuestionIdAndPlayerId(roundId, questionId, playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));

        return ResponseEntity.ok(answer);
    }
}
