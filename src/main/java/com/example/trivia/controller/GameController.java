package com.example.trivia.controller;

import com.example.trivia.dto.AnswerSubmissionRequest;
import com.example.trivia.dto.GameCreationRequest;
import com.example.trivia.model.*;
import com.example.trivia.repository.*;

import jakarta.servlet.http.HttpSession;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
public class GameController {
    private final AnswerRepository answerRepo;
    private final GameRepository gameRepo;
    private final QuestionRepository questionRepo;
    private final RoomRepository roomRepo;
    private final RoundQuestionRepository roundQuestionRepo;
    private final RoundRepository roundRepo;

    public GameController(
            AnswerRepository answerRepo,
            GameRepository gameRepo,
            QuestionRepository questionRepo,
            RoomRepository roomRepo,
            RoundQuestionRepository roundQuestionRepo,
            RoundRepository roundRepo) {
        this.answerRepo = answerRepo;
        this.gameRepo = gameRepo;
        this.questionRepo = questionRepo;
        this.roomRepo = roomRepo;
        this.roundQuestionRepo = roundQuestionRepo;
        this.roundRepo = roundRepo;
    }

    @GetMapping("/games")
    public ResponseEntity<List<Game>> getGames(@RequestParam(required = false) Long roomId) {
        List<Game> games = gameRepo.findByRoomId(roomId);
        return ResponseEntity.ok(games);
    }

    @PostMapping("/games")
    public ResponseEntity<Game> createGame(
            @RequestBody GameCreationRequest request,
            HttpSession session) {
        roomRepo.findById(request.roomId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room id"));

        Player currentPlayer = (Player) session.getAttribute(request.roomId().toString());
        if (currentPlayer == null || !currentPlayer.isHost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can create a game");
        }

        Game game = new Game();
        game.setRoomId(request.roomId());
        game.setRounds(request.rounds());
        game.setTimePerRound(request.timePerRound());
        game.setQuestionsPerRound(request.questionsPerRound());
        game.setDifficulty(request.difficulty());
        game.setCreatedAt(Instant.now());
        game.setEndedAt(game.getCreatedAt().plus(
                Duration.ofSeconds(game.getRounds() * game.getTimePerRound())));
        gameRepo.save(game);

        // Create rounds and questions for the game, based on the game's settings
        Set<Long> questionIds = new HashSet<>();
        long count = questionRepo.count();
        for (int roundNumber = 1; roundNumber <= game.getRounds(); roundNumber++) {
            Round round = new Round();
            round.setGameId(game.getGameId());
            round.setRoundNumber(roundNumber);
            round.setCreatedAt(Instant.now().plus(
                    Duration.ofSeconds(game.getTimePerRound() * (roundNumber - 1))));
            round.setEndedAt(round.getCreatedAt().plus(
                    Duration.ofSeconds(game.getTimePerRound())));
            roundRepo.save(round);

            for (int questionNumber = 1; questionNumber <= game.getQuestionsPerRound(); questionNumber++) {
                // Find a random question, based on the game's settings
                Question question = null;
                while (question == null || questionIds.contains(question.getQuestionId())) {
                    question = questionRepo.findByDifficulty(game.getDifficulty(),
                            PageRequest.of((int) (Math.random() * count), 1))
                            .getContent().get(0);
                }

                // Store questionId to avoid repeating questions
                questionIds.add(question.getQuestionId());

                RoundQuestion roundQuestion = new RoundQuestion();
                roundQuestion.setRoundId(round.getRoundId());
                roundQuestion.setQuestionId(question.getQuestionId());
                roundQuestionRepo.save(roundQuestion);
            }
        }

        URI location = URI.create("/games/" + game.getGameId());
        return ResponseEntity.created(location).body(game);
    }

    @GetMapping("/games/{gameId}")
    public ResponseEntity<Game> getGame(@PathVariable Long gameId) {
        Game game = gameRepo.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        return ResponseEntity.ok(game);
    }

    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<Void> deleteGame(@PathVariable Long gameId, HttpSession session) {
        Game game = gameRepo.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        Long roomId = game.getRoomId();
        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null || !currentPlayer.isHost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete the game");
        }

        gameRepo.deleteById(gameId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/games/{gameId}/rounds")
    public ResponseEntity<List<Round>> getRounds(@PathVariable Long gameId) {
        gameRepo.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        List<Round> rounds = roundRepo.findByGameId(gameId);
        return ResponseEntity.ok(rounds);
    }

    @GetMapping("/games/{gameId}/rounds/{roundId}/questions")
    public ResponseEntity<List<Question>> getRoundQuestions(
            @PathVariable Long gameId,
            @PathVariable Long roundId) {
        gameRepo.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        Round round = roundRepo.findById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (Instant.now().isBefore(round.getCreatedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round has not started yet");
        }

        List<Question> questions = questionRepo.findByRoundId(roundId);

        // MUST NOT show the correct answers here!
        for (Question question : questions) {
            question.setCorrectAnswers(null);
        }

        return ResponseEntity.ok(questions);
    }

    @PostMapping("/games/{gameId}/rounds/{roundId}/questions/{questionId}")
    public ResponseEntity<Void> submitAnswer(
            @PathVariable Long gameId,
            @PathVariable Long roundId,
            @PathVariable Long questionId,
            @RequestBody AnswerSubmissionRequest request,
            HttpSession session) {
        Game game = gameRepo.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        Round round = roundRepo.findById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (Instant.now().isAfter(round.getEndedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round has already ended");
        }

        Question question = questionRepo.findById(questionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        Long roomId = game.getRoomId();
        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null || !currentPlayer.getRoomId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player is not in the room of this game");
        }

        Answer answer = new Answer();
        answer.setGameId(gameId);
        answer.setRoundId(roundId);
        answer.setQuestionId(questionId);
        answer.setPlayerId(currentPlayer.getPlayerId());
        answer.setAnswer(request.answer());
        answer.setCreatedAt(Instant.now());
        answer.setCorrect(question.getCorrectAnswers().stream()
            .anyMatch(correctAnswer -> answer.getAnswer().equalsIgnoreCase(correctAnswer)));
        answerRepo.save(answer);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/games/{gameId}/rounds/{roundId}/questions/{questionId}")
    public ResponseEntity<List<Answer>> getAnswers(
            @PathVariable Long gameId,
            @PathVariable Long roundId,
            @PathVariable Long questionId) {
        gameRepo.findById(gameId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        Round round = roundRepo.findById(roundId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (Instant.now().isBefore(round.getEndedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round has not ended yet");
        }

        questionRepo.findById(questionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        List<Answer> answers = answerRepo.findByRoundIdAndQuestionId(roundId, questionId);
        return ResponseEntity.ok(answers);
    }
}
