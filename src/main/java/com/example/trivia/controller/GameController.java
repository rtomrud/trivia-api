package com.example.trivia.controller;

import com.example.trivia.model.*;
import com.example.trivia.repository.*;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
public class GameController {
    private final GameRepository gameRepo;
    private final RoomRepository roomRepo;
    private final RoundRepository roundRepo;
    private final QuestionRepository questionRepo;
    private final PlayerRepository playerRepo;
    private final AnswerRepository answerRepo;
    private final SettingsRepository settingsRepo;

    public GameController(
            GameRepository gameRepo,
            RoomRepository roomRepo,
            RoundRepository roundRepo,
            QuestionRepository questionRepo,
            PlayerRepository playerRepo,
            AnswerRepository answerRepo,
            SettingsRepository settingsRepo,
            TeamRepository teamRepo) {
        this.gameRepo = gameRepo;
        this.roomRepo = roomRepo;
        this.roundRepo = roundRepo;
        this.questionRepo = questionRepo;
        this.playerRepo = playerRepo;
        this.answerRepo = answerRepo;
        this.settingsRepo = settingsRepo;
    }

    @PostMapping("/games")
    public ResponseEntity<Game> createGame(@RequestBody Map<String, Object> body,
            HttpSession session) {
        String roomId = (String) body.get("roomId");
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Player player = (Player) session.getAttribute(roomId);
        if (player == null || !player.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Room room = roomOptional.get();
        Optional<Settings> settingsOptional = settingsRepo.findById(room.getSettingsId());
        Settings settings = settingsOptional.get();

        Game game = new Game();
        game.setGameId(UUID.randomUUID().toString());
        game.setRoomId(roomId);
        game.setCreatedAt(Instant.now());
        game.setEndedAt(Instant.now().plus(
                Duration.ofSeconds(settings.getRounds() * settings.getTimePerRound())));
        game.setSettingsId(room.getSettingsId()); // Game uses Room's settings
        gameRepo.save(game);

        // Create rounds for the game, based on the game's settings
        for (int roundNumber = 1; roundNumber <= settings.getRounds(); roundNumber++) {
            Round round = new Round();
            round.setRoundId(UUID.randomUUID().toString());
            round.setGameId(game.getGameId());
            round.setRoundNumber(roundNumber);
            round.setCreatedAt(Instant.now().plus(
                    Duration.ofSeconds(settings.getTimePerRound() * (roundNumber - 1))));
            round.setEndedAt(round.getCreatedAt().plus(
                    Duration.ofSeconds(settings.getTimePerRound())));
            roundRepo.save(round);
        }

        URI location = URI.create("/games/" + game.getGameId());
        return ResponseEntity.created(location).body(game);
    }

    @GetMapping("/games/{gameId}")
    public ResponseEntity<Game> getGame(@PathVariable String gameId) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        if (gameOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Game game = gameOptional.get();
        return ResponseEntity.ok(game);
    }

    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<Void> deleteGame(@PathVariable String gameId, HttpSession session) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        if (gameOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Game game = gameOptional.get();
        Player player = (Player) session.getAttribute(game.getRoomId());
        if (player == null || !player.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        gameRepo.deleteById(gameId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/games/{gameId}/rounds")
    public ResponseEntity<List<Round>> getRounds(@PathVariable String gameId) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        if (gameOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Round> rounds = roundRepo.findByGameId(gameId);
        return ResponseEntity.ok(rounds);
    }

    @GetMapping("/games/{gameId}/rounds/{roundId}/questions")
    public ResponseEntity<List<Question>> getRoundQuestions(@PathVariable String gameId,
            @PathVariable String roundId) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        Optional<Round> roundOptional = roundRepo.findById(roundId);
        if (gameOptional.isEmpty() || roundOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Round round = roundOptional.get();
        if (Instant.now().isBefore(round.getCreatedAt())) {
            return ResponseEntity.badRequest().build(); // Round has not started yet
        }

        List<Question> questions = questionRepo.findByRoundId(roundId);

        // MUST NOT show the correct answers here!
        for (Question question : questions) {
            question.setCorrectAnswers(null);
        }

        return ResponseEntity.ok(questions);
    }

    @PostMapping("/games/{gameId}/rounds/{roundId}/questions/{questionId}/players/{playerId}")
    public ResponseEntity<Void> submitAnswer(@PathVariable String gameId,
            @PathVariable String roundId,
            @PathVariable String questionId,
            @PathVariable String playerId,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        Optional<Player> playerOptional = playerRepo.findById(playerId);
        Optional<Question> questionOptional = questionRepo.findById(questionId);
        Optional<Round> roundOptional = roundRepo.findById(roundId);
        if (gameOptional.isEmpty()
                || playerOptional.isEmpty()
                || questionOptional.isEmpty()
                || roundOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Round round = roundOptional.get();
        if (Instant.now().isAfter(round.getEndedAt())) {
            return ResponseEntity.badRequest().build(); // Round has already ended
        }

        Game game = gameOptional.get();
        Player player = (Player) session.getAttribute(game.getRoomId());
        if (player == null || !player.getPlayerId().equals(playerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Answer answer = new Answer();
        answer.setAnswerId(UUID.randomUUID().toString());
        answer.setQuestionId(questionId);
        answer.setPlayerId(playerId);
        answer.setAnswer((String) body.get("answer"));

        // Check whether the submited answer is correct
        Question question = questionOptional.get();
        answer.setCorrect(false);
        for (String correctAnswer : question.getCorrectAnswers()) {
            if (answer.getAnswer().equalsIgnoreCase(correctAnswer)) {
                answer.setCorrect(true);
            }
        }

        answerRepo.save(answer);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/games/{gameId}/rounds/{roundId}/questions/{questionId}/players/{playerId}")
    public ResponseEntity<Answer> getAnswer(
            @PathVariable String gameId,
            @PathVariable String roundId,
            @PathVariable String questionId,
            @PathVariable String playerId,
            HttpSession session) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        Optional<Player> playerOptional = playerRepo.findById(playerId);
        Optional<Question> questionOptional = questionRepo.findById(questionId);
        Optional<Round> roundOptional = roundRepo.findById(roundId);
        Optional<Answer> answerOptional = answerRepo.findByQuestionIdAndPlayerId(questionId, playerId);
        if (gameOptional.isEmpty()
                || playerOptional.isEmpty()
                || questionOptional.isEmpty()
                || roundOptional.isEmpty()
                || answerOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Round round = roundOptional.get();
        if (Instant.now().isBefore(round.getEndedAt())) {
            return ResponseEntity.badRequest().build(); // Round has not ended yet
        }

        Game game = gameOptional.get();
        Player player = (Player) session.getAttribute(game.getRoomId());
        if (player == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Answer answer = answerOptional.get();
        return ResponseEntity.ok(answer);
    }
}
