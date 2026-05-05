package com.example.trivia.controller;

import com.example.trivia.model.Game;
import com.example.trivia.model.Question;
import com.example.trivia.model.Room;
import com.example.trivia.model.Round;
import com.example.trivia.repository.GameRepository;
import com.example.trivia.repository.QuestionRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.RoundRepository;
import com.example.trivia.service.SseService;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameControllerTest {
    @Mock
    private GameRepository gameRepo;

    @Mock
    private QuestionRepository questionRepo;

    @Mock
    private RoomRepository roomRepo;

    @Mock
    private RoundRepository roundRepo;

    @Mock
    private SseService sseService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private GameController gameController;

    private Game testGame;
    private Room testRoom;
    private Question testQuestion;

    @BeforeEach
    void setUp() {
        testGame = new Game();
        testGame.setId(1L);
        testGame.setRoomId(1L);
        testGame.setCreatedAt(Instant.now());
        testGame.setEndedAt(Instant.now().plus(Duration.ofMinutes(120)));

        testRoom = new Room();
        testRoom.setId(1L);
        testRoom.setCreatedAt(Instant.now());
        testRoom.setHostId(1L);

        testQuestion = new Question();
        testQuestion.setId(1L);
    }

    @Test
    void getGames_returnsGamesList() {
        Game game1 = new Game();
        game1.setId(1L);
        game1.setRoomId(1L);

        Game game2 = new Game();
        game2.setId(2L);
        game2.setRoomId(1L);

        Page<Game> gamesPage = new PageImpl<>(Arrays.asList(game1, game2), PageRequest.of(0, 10), 2);
        when(gameRepo.findByRoomId(1L, PageRequest.of(0, 10))).thenReturn(gamesPage);

        ResponseEntity<List<Game>> response = gameController.getGames(1L, 0, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        verify(gameRepo).findByRoomId(1L, PageRequest.of(0, 10));
    }

    @Test
    void createGame_createsNewGameAndReturns201() {
        when(request.getAttribute("playerId")).thenReturn(1L);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        when(roomRepo.save(any(Room.class))).thenReturn(testRoom);
        when(questionRepo.count()).thenReturn(1L);
        when(questionRepo.findAll(any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(testQuestion), PageRequest.of(0, 1), 1));
        when(gameRepo.save(any(Game.class))).thenAnswer(invocation -> {
            Game g = invocation.getArgument(0);
            g.setId(1L);
            return g;
        });

        ResponseEntity<Game> response = gameController.createGame(1L, 1, 60, 1, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());

        ArgumentCaptor<Game> gameCaptor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepo).save(gameCaptor.capture());
        assertEquals(Duration.ofSeconds(60),
                Duration.between(gameCaptor.getValue().getCreatedAt(), gameCaptor.getValue().getEndedAt()));
        verify(roundRepo).save(any(Round.class));
    }

    @Test
    void createGame_throws401WhenNotAuthenticated() {
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameController.createGame(1L, 2, 60, 3, request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void createGame_throws403WhenNotHost() {
        when(request.getAttribute("playerId")).thenReturn(2L);
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameController.createGame(1L, 2, 60, 3, request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(roomRepo).findById(1L);
    }

    @Test
    void getGame_returnsGameWhenFound() {
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));

        ResponseEntity<Game> response = gameController.getGame(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testGame, response.getBody());
        verify(gameRepo).findById(1L);
    }

    @Test
    void getGame_throws404WhenGameNotFound() {
        when(gameRepo.findById(1L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameController.getGame(1L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(gameRepo).findById(1L);
    }

    @Test
    void deleteGame_deletesGameWhenHost() {
        when(request.getAttribute("playerId")).thenReturn(1L);
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));

        ResponseEntity<Void> response = gameController.deleteGame(1L, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(gameRepo).deleteById(1L);
    }

    @Test
    void deleteGame_throws401WhenNotAuthenticated() {
        when(request.getAttribute("playerId")).thenReturn(null);
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameController.deleteGame(1L, request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(gameRepo).findById(1L);
        verify(roomRepo).findById(1L);
    }

    @Test
    void deleteGame_throws403WhenNotHost() {
        when(request.getAttribute("playerId")).thenReturn(2L);
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> gameController.deleteGame(1L, request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(gameRepo).findById(1L);
        verify(roomRepo).findById(1L);
    }
}
