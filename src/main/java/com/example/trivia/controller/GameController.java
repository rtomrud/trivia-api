package com.example.trivia.controller;

import com.example.trivia.dto.AnswerSubmissionRequest;
import com.example.trivia.dto.GameCreationRequest;
import com.example.trivia.model.Answer;
import com.example.trivia.model.Game;
import com.example.trivia.model.Player;
import com.example.trivia.model.Question;
import com.example.trivia.model.Room;
import com.example.trivia.model.Round;
import com.example.trivia.model.RoundQuestion;
import com.example.trivia.repository.AnswerRepository;
import com.example.trivia.repository.GameRepository;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.QuestionRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.RoundQuestionRepository;
import com.example.trivia.repository.RoundRepository;
import com.example.trivia.util.LinkHeaderBuilder;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

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

    @GetMapping("/games")
    public ResponseEntity<List<Game>> getGames(
            @RequestParam(required = false) Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Game> games = roomId != null
                ? gameRepo.findByRoomId(roomId, pageable)
                : gameRepo.findAll(pageable);

        String url = UriComponentsBuilder.fromPath("/games")
                .replaceQueryParam("roomId", roomId)
                .replaceQueryParam("page", page)
                .replaceQueryParam("size", size)
                .toUriString();

        String linkHeader = LinkHeaderBuilder.buildWithPaginationLinks(
                games.getNumber(),
                games.getSize(),
                games.getTotalPages(),
                url);

        return ResponseEntity.ok().header("Link", linkHeader).body(games.toList());
    }

    @PostMapping("/games")
    public ResponseEntity<Game> createGame(@RequestBody GameCreationRequest body, HttpServletRequest request) {
        Room room = roomRepo.findById(body.roomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room id"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can create a game");
        }

        Game game = new Game();
        game.setRoomId(body.roomId());
        game.setCreatedAt(Instant.now());
        game.setEndedAt(game.getCreatedAt().plus(Duration.ofSeconds(body.rounds() * body.timePerRound())));
        game = gameRepo.save(game);

        // Create rounds and questions for the game, based on the game's settings
        Set<Long> questionIds = new HashSet<>();
        long questionCount = questionRepo.count();
        if (questionCount < body.rounds() * body.questionsPerRound()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough questions");
        }

        for (int roundNumber = 1; roundNumber <= body.rounds(); roundNumber++) {
            Round round = new Round();
            round.setGameId(game.getGameId());
            round.setRoundNumber(roundNumber);
            round.setCreatedAt(Instant.now().plus(Duration.ofSeconds(body.timePerRound() * (roundNumber - 1))));
            round.setEndedAt(round.getCreatedAt().plus(Duration.ofSeconds(body.timePerRound())));
            round = roundRepo.save(round);

            for (int questionNumber = 1; questionNumber <= body.questionsPerRound(); questionNumber++) {
                // Find a random question, based on the game's settings
                Question question = null;
                while (question == null || questionIds.contains(question.getQuestionId())) {
                    Pageable pageable = PageRequest.of((int) (Math.random() * questionCount), 1);
                    question = questionRepo.findAll(pageable).getContent().get(0);
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
    public ResponseEntity<Void> deleteGame(@PathVariable Long gameId, HttpServletRequest request) {
        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        Room room = roomRepo.findById(game.getRoomId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        if (!room.getHostId().equals(currentPlayerId)) {
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
    public ResponseEntity<List<Question>> getRoundQuestions(@PathVariable Long gameId, @PathVariable Long roundId) {
        gameRepo.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        Round round = roundRepo.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        if (Instant.now().isBefore(round.getCreatedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round has not started yet");
        }

        List<Question> questions = questionRepo.findByRoundId(roundId);
        for (Question question : questions) {
            question.setCorrectAnswers(null); // Don't show correct answers until round is over
        }

        return ResponseEntity.ok(questions);
    }

    @PostMapping("/games/{gameId}/rounds/{roundId}/questions/{questionId}")
    public ResponseEntity<Void> submitAnswer(
            @PathVariable Long gameId,
            @PathVariable Long roundId,
            @PathVariable Long questionId,
            @RequestBody AnswerSubmissionRequest body,
            HttpServletRequest request) {
        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        Round round = roundRepo.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        Question question = questionRepo.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        Long currentPlayerId = (Long) request.getAttribute("playerId");
        if (currentPlayerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated");
        }

        Player currentPlayer = playerRepo.findById(currentPlayerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player not authenticated"));

        if (Instant.now().isAfter(round.getEndedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round has already ended");
        }

        if (!currentPlayer.getRoomId().equals(game.getRoomId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Player is not in the room");
        }

        Answer answer = new Answer();
        answer.setRoundId(roundId);
        answer.setQuestionId(questionId);
        answer.setPlayerId(currentPlayer.getPlayerId());
        answer.setAnswer(body.answer());
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

        questionRepo.findById(questionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));

        if (Instant.now().isBefore(round.getEndedAt())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round has not ended yet");
        }

        List<Answer> answers = answerRepo.findByRoundIdAndQuestionId(roundId, questionId);
        return ResponseEntity.ok(answers);
    }
}
