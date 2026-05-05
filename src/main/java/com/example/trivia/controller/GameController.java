package com.example.trivia.controller;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.trivia.model.Game;
import com.example.trivia.model.Question;
import com.example.trivia.model.Room;
import com.example.trivia.model.Round;
import com.example.trivia.repository.GameRepository;
import com.example.trivia.repository.QuestionRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.RoundRepository;
import com.example.trivia.service.SseService;
import com.example.trivia.util.LinkHeaderBuilder;

@RestController
public class GameController {
    private final GameRepository gameRepo;
    private final QuestionRepository questionRepo;
    private final RoomRepository roomRepo;
    private final RoundRepository roundRepo;
    private final SseService sseService;

    public GameController(
            GameRepository gameRepo,
            QuestionRepository questionRepo,
            RoomRepository roomRepo,
            RoundRepository roundRepo,
            SseService sseService) {
        this.gameRepo = gameRepo;
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
    public ResponseEntity<Game> createGame(
            @RequestParam Long roomId,
            @RequestParam Integer rounds,
            @RequestParam Integer timePerRound,
            @RequestParam Integer questionsPerRound,
            HttpServletRequest request) {
        Room room = roomRepo.findById(roomId)
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
        game.setRoomId(roomId);
        game.setCreatedAt(Instant.now());
        game.setEndedAt(game.getCreatedAt().plus(Duration.ofSeconds(rounds * timePerRound)));
        game = gameRepo.save(game);

        room.setGameId(game.getId());
        room = roomRepo.save(room);

        Set<Long> questionIds = new HashSet<>();
        long questionCount = questionRepo.count();
        if (questionCount < rounds * questionsPerRound) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough questions");
        }

        for (int roundNumber = 1; roundNumber <= rounds; roundNumber++) {
            Round round = new Round();
            round.setGameId(game.getId());
            round.setCreatedAt(Instant.now().plus(Duration.ofSeconds(timePerRound * (roundNumber - 1))));
            round.setEndedAt(round.getCreatedAt().plus(Duration.ofSeconds(timePerRound)));

            for (int questionNumber = 1; questionNumber <= questionsPerRound; questionNumber++) {
                Question question = null;
                while (question == null || questionIds.contains(question.getId())) {
                    Pageable pageable = PageRequest.of((int) (Math.random() * questionCount), 1);
                    question = questionRepo.findAll(pageable).getContent().get(0);
                }

                questionIds.add(question.getId());
                round.addQuestion(question);
            }

            roundRepo.save(round);
        }

        URI location = URI.create("/games/" + game.getId());
        sseService.publish(room.getId().toString(), "game-created", game.getId());
        return ResponseEntity.created(location).body(game);
    }

    @GetMapping("/games/{id}")
    public ResponseEntity<Game> getGame(@PathVariable Long id) {
        Game game = gameRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found"));

        return ResponseEntity.ok(game);
    }

    @DeleteMapping("/games/{id}")
    public ResponseEntity<Void> deleteGame(@PathVariable Long id, HttpServletRequest request) {
        Game game = gameRepo.findById(id)
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

        gameRepo.deleteById(id);
        sseService.publish(room.getId().toString(), "game-deleted", game.getId());
        return ResponseEntity.noContent().build();
    }
}
