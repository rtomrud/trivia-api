package com.example.trivia.controller;

import com.example.trivia.model.*;
import com.example.trivia.repository.*;

import jakarta.servlet.http.HttpSession;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
public class GameController {
    private final AnswerRepository answerRepo;
    private final GameRepository gameRepo;
    private final PlayerRepository playerRepo;
    private final QuestionRepository questionRepo;
    private final RoomRepository roomRepo;
    private final RoundQuestionRepository roundQuestionRepo;
    private final RoundRepository roundRepo;

    public GameController(
            AnswerRepository answerRepo,
            GameRepository gameRepo,
            PlayerRepository playerRepo,
            QuestionRepository questionRepo,
            RoomRepository roomRepo,
            RoundQuestionRepository roundQuestionRepo,
            RoundRepository roundRepo) {
        this.answerRepo = answerRepo;
        this.gameRepo = gameRepo;
        this.playerRepo = playerRepo;
        this.questionRepo = questionRepo;
        this.roomRepo = roomRepo;
        this.roundQuestionRepo = roundQuestionRepo;
        this.roundRepo = roundRepo;
    }

    @PostMapping("/games")
    public ResponseEntity<Game> createGame(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        Long roomId = (Long) body.get("roomId");
        Optional<Room> roomOptional = roomRepo.findById(roomId);
        if (roomOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null || !currentPlayer.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Game game = new Game();
        game.setRoomId(roomId);
        game.setRounds((Integer) body.get("rounds"));
        game.setTimePerRound((Integer) body.get("timePerRound"));
        game.setQuestionsPerRound((Integer) body.get("questionsPerRound"));
        game.setDifficulty((Integer) body.get("difficulty"));
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
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        if (gameOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Game game = gameOptional.get();
        return ResponseEntity.ok(game);
    }

    @DeleteMapping("/games/{gameId}")
    public ResponseEntity<Void> deleteGame(@PathVariable Long gameId, HttpSession session) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        if (gameOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Game game = gameOptional.get();
        Long roomId = game.getRoomId();
        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null || !currentPlayer.isHost()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        gameRepo.deleteById(gameId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/games/{gameId}/rounds")
    public ResponseEntity<List<Round>> getRounds(@PathVariable Long gameId) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        if (gameOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Round> rounds = roundRepo.findByGameId(gameId);
        return ResponseEntity.ok(rounds);
    }

    @GetMapping("/games/{gameId}/rounds/{roundId}/questions")
    public ResponseEntity<List<Question>> getRoundQuestions(
            @PathVariable Long gameId,
            @PathVariable Long roundId) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        Optional<Round> roundOptional = roundRepo.findById(roundId);
        if (gameOptional.isEmpty() || roundOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Check if round has already started
        Round round = roundOptional.get();
        if (Instant.now().isBefore(round.getCreatedAt())) {
            return ResponseEntity.badRequest().build();
        }

        List<Question> questions = questionRepo.findByRoundId(roundId);

        // MUST NOT show the correct answers here!
        for (Question question : questions) {
            question.setCorrectAnswers(null);
        }

        return ResponseEntity.ok(questions);
    }

    @PostMapping("/games/{gameId}/rounds/{roundId}/questions/{questionId}/players/{playerId}")
    public ResponseEntity<Void> submitAnswer(
            @PathVariable Long gameId,
            @PathVariable Long roundId,
            @PathVariable Long questionId,
            @PathVariable Long playerId,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        Optional<Round> roundOptional = roundRepo.findById(roundId);
        Optional<Question> questionOptional = questionRepo.findById(questionId);
        Optional<Player> playerOptional = playerRepo.findById(playerId);
        if (gameOptional.isEmpty()
                || roundOptional.isEmpty()
                || questionOptional.isEmpty()
                || playerOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Check if round has not ended yet
        Round round = roundOptional.get();
        if (Instant.now().isAfter(round.getEndedAt())) {
            return ResponseEntity.badRequest().build();
        }

        Game game = gameOptional.get();
        Long roomId = game.getRoomId();
        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null || !currentPlayer.getPlayerId().equals(playerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Answer answer = new Answer();
        answer.setGameId(gameId);
        answer.setRoundId(roundId);
        answer.setQuestionId(questionId);
        answer.setPlayerId(playerId);
        answer.setCreatedAt(Instant.now());
        answer.setAnswer((String) body.get("answer"));

        // Check if the submited answer is correct
        Question question = questionOptional.get();
        boolean correct = false;
        for (String correctAnswer : question.getCorrectAnswers()) {
            if (answer.getAnswer().equalsIgnoreCase(correctAnswer)) {
                correct = true;
                break;
            }
        }

        answer.setCorrect(correct);
        answerRepo.save(answer);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/games/{gameId}/rounds/{roundId}/questions/{questionId}/players/{playerId}")
    public ResponseEntity<Answer> getAnswer(
            @PathVariable Long gameId,
            @PathVariable Long roundId,
            @PathVariable Long questionId,
            @PathVariable Long playerId,
            HttpSession session) {
        Optional<Game> gameOptional = gameRepo.findById(gameId);
        Optional<Round> roundOptional = roundRepo.findById(roundId);
        Optional<Question> questionOptional = questionRepo.findById(questionId);
        Optional<Player> playerOptional = playerRepo.findById(playerId);
        Optional<Answer> answerOptional = answerRepo
                .findByRoundIdAndQuestionIdAndPlayerId(roundId, questionId, playerId);
        if (gameOptional.isEmpty()
                || roundOptional.isEmpty()
                || questionOptional.isEmpty()
                || playerOptional.isEmpty()
                || answerOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Check if round has already ended
        Round round = roundOptional.get();
        if (Instant.now().isBefore(round.getEndedAt())) {
            return ResponseEntity.badRequest().build();
        }

        Game game = gameOptional.get();
        Long roomId = game.getRoomId();
        Player currentPlayer = (Player) session.getAttribute(roomId.toString());
        if (currentPlayer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Answer answer = answerOptional.get();
        return ResponseEntity.ok(answer);
    }
}
