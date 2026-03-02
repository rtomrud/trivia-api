package com.example.trivia.controller;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

import com.example.trivia.dto.AnswerSubmissionRequest;
import com.example.trivia.dto.GameCreationRequest;
import com.example.trivia.model.Answer;
import com.example.trivia.model.Game;
import com.example.trivia.model.Player;
import com.example.trivia.model.Question;
import com.example.trivia.model.QuestionRef;
import com.example.trivia.model.Room;
import com.example.trivia.model.Round;
import com.example.trivia.repository.AnswerRepository;
import com.example.trivia.repository.GameRepository;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.QuestionRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.RoundRepository;
import com.example.trivia.service.SseService;
import com.example.trivia.util.LinkHeaderBuilder;

@RestController
public class GameController {
    private final AnswerRepository answerRepo;
    private final GameRepository gameRepo;
    private final PlayerRepository playerRepo;
    private final QuestionRepository questionRepo;
    private final RoomRepository roomRepo;
    private final RoundRepository roundRepo;
    private final SseService sseService;

    public GameController(
            AnswerRepository answerRepo,
            GameRepository gameRepo,
            PlayerRepository playerRepo,
            QuestionRepository questionRepo,
            RoomRepository roomRepo,
            RoundRepository roundRepo,
            SseService sseService) {
        this.answerRepo = answerRepo;
        this.gameRepo = gameRepo;
        this.playerRepo = playerRepo;
        this.questionRepo = questionRepo;
        this.roomRepo = roomRepo;
        this.roundRepo = roundRepo;
        this.sseService = sseService;
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

        if (room.getGameId() != null) {
            gameRepo.findById(room.getGameId())
                    .ifPresent((Game game) -> {
                        if (Instant.now().isBefore(game.getEndedAt())) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Cannot create a new game during a game");
                        }
                    });

        }

        Game game = new Game();
        game.setRoomId(body.roomId());
        game.setCreatedAt(Instant.now());
        game.setEndedAt(game.getCreatedAt().plus(Duration.ofSeconds(body.rounds() * body.timePerRound())));
        game = gameRepo.save(game);

        room.setGameId(game.getId());
        room = roomRepo.save(room);

        // Create rounds and questions for the game, based on the game's settings
        Set<Long> questionIds = new HashSet<>();
        long questionCount = questionRepo.count();
        if (questionCount < body.rounds() * body.questionsPerRound()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough questions");
        }

        for (int roundNumber = 1; roundNumber <= body.rounds(); roundNumber++) {
            Round round = new Round();
            round.setGameId(game.getId());
            round.setCreatedAt(Instant.now().plus(Duration.ofSeconds(body.timePerRound() * (roundNumber - 1))));
            round.setEndedAt(round.getCreatedAt().plus(Duration.ofSeconds(body.timePerRound())));

            for (int questionNumber = 1; questionNumber <= body.questionsPerRound(); questionNumber++) {
                // Find a random question, based on the game's settings
                Question question = null;
                while (question == null || questionIds.contains(question.getId())) {
                    Pageable pageable = PageRequest.of((int) (Math.random() * questionCount), 1);
                    question = questionRepo.findAll(pageable).getContent().get(0);
                }

                // Store questionId to avoid repeating questions
                questionIds.add(question.getId());

                round.addQuestion(question);
            }

            round = roundRepo.save(round);
        }

        URI location = URI.create("/games/" + game.getId());
        sseService.publish(room.getId().toString(), "game-created", game.getId());
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

        if (Instant.now().isAfter(game.getEndedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete an ended game");
        }

        gameRepo.deleteById(gameId);
        sseService.publish(room.getId().toString(), "game-deleted", game.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/games/{gameId}/rounds")
    public ResponseEntity<List<Round>> getRounds(@PathVariable Long gameId) {
        gameRepo.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        List<Round> rounds = roundRepo.findByGameId(gameId);
        return ResponseEntity.ok(rounds);
    }

    @GetMapping("/games/{gameId}/rounds/{roundId}")
    public ResponseEntity<Round> getRound(@PathVariable Long gameId, @PathVariable Long roundId) {
        gameRepo.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        Round round = roundRepo.findById(roundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Round not found"));

        return ResponseEntity.ok(round);
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

        // Don't show correct answers until round is over
        if (Instant.now().isBefore(round.getEndedAt())) {
            for (Question question : questions) {
                List<String> correctAnswers = new ArrayList<>();
                question.setCorrectAnswers(correctAnswers);
            }
        }

        return ResponseEntity.ok(questions);
    }

    @PostMapping("/games/{gameId}/rounds/{roundId}/questions/{questionId}")
    public ResponseEntity<Answer> submitAnswer(
            @PathVariable Long gameId,
            @PathVariable Long roundId,
            @PathVariable Long questionId,
            @RequestBody AnswerSubmissionRequest body,
            HttpServletRequest request) {
        Game game = gameRepo.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

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

    @GetMapping("/games/{gameId}/rounds/{roundId}/questions/{questionId}/players/{playerId}")
    public ResponseEntity<Answer> getAnswer(
            @PathVariable Long gameId,
            @PathVariable Long roundId,
            @PathVariable Long questionId,
            @PathVariable Long playerId,
            HttpServletRequest request) {
        gameRepo.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

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
