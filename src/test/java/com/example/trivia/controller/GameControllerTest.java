package com.example.trivia.controller;

import com.example.trivia.dto.GameCreationRequest;
import com.example.trivia.model.Game;
import com.example.trivia.model.Player;
import com.example.trivia.model.Question;
import com.example.trivia.model.Room;
import com.example.trivia.model.Round;
import com.example.trivia.repository.AnswerRepository;
import com.example.trivia.repository.GameRepository;
import com.example.trivia.repository.PlayerRepository;
import com.example.trivia.repository.QuestionRepository;
import com.example.trivia.repository.RoomRepository;
import com.example.trivia.repository.RoundQuestionRepository;
import com.example.trivia.repository.RoundRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
    private AnswerRepository answerRepo;
    
    @Mock
    private GameRepository gameRepo;
    
    @Mock
    private PlayerRepository playerRepo;
    
    @Mock
    private QuestionRepository questionRepo;
    
    @Mock
    private RoomRepository roomRepo;
    
    @Mock
    private RoundQuestionRepository roundQuestionRepo;
    
    @Mock
    private RoundRepository roundRepo;
    
    @InjectMocks
    private GameController controller;
    
    private Game testGame;
    private Room testRoom;
    private Player testPlayer;
    private Question testQuestion;
    private Round testRound;
    
    @BeforeEach
    void setUp() {
        testGame = new Game();
        testGame.setId(1L);
        testGame.setRoomId(1L);
        testGame.setCreatedAt(Instant.now());
        
        testRoom = new Room();
        testRoom.setId(1L);
        testRoom.setHostId(1L);
        
        testPlayer = new Player();
        testPlayer.setId(1L);
        testPlayer.setRoomId(1L);
        testPlayer.setUsername("testUser");
        
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
    void getGames_returnsGamesList() throws Exception {
        Game game1 = new Game();
        game1.setId(1L);
        game1.setRoomId(1L);
        
        Game game2 = new Game();
        game2.setId(2L);
        game2.setRoomId(1L);
        
        Page<Game> gamesPage = new PageImpl<>(Arrays.asList(game1, game2), PageRequest.of(0, 10), 2);
        when(gameRepo.findByRoomId(1L, PageRequest.of(0, 10))).thenReturn(gamesPage);
        
        ResponseEntity<List<Game>> response = controller.getGames(1L, 0, 10);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        verify(gameRepo).findByRoomId(1L, PageRequest.of(0, 10));
    }
    
    @Test
    void createGame_createsNewGameAndReturns201() {
        GameCreationRequest request = new GameCreationRequest(1L, 2, 60, 0);
        
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute("playerId")).thenReturn(1L);
        
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        Game savedGame = new Game();
        savedGame.setId(1L);
        savedGame.setRoomId(1L);
        savedGame.setCreatedAt(Instant.now());
        savedGame.setEndedAt(savedGame.getCreatedAt().plus(Duration.ofMinutes(120))); // 2 rounds * 60 seconds
        when(gameRepo.save(any(Game.class))).thenReturn(savedGame);
        
        ResponseEntity<Game> response = controller.createGame(request, httpRequest);
        
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(savedGame.getId(), response.getBody().getId());
        assertEquals(1L, response.getBody().getId());
        assertNotNull(response.getBody().getCreatedAt());
        assertNotNull(response.getBody().getEndedAt());
        
        verify(gameRepo).save(any(Game.class));
        verify(roundRepo, times(2)).save(any(Round.class));
        verify(httpRequest).getAttribute("playerId");
    }
    
    @Test
    void createGame_throws401WhenNotAuthenticated() {
        GameCreationRequest request = new GameCreationRequest(1L, 2, 60, 3);
        
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.createGame(request, httpRequest);
        });
    }
    
    @Test
    void createGame_throws403WhenNotHost() {
        GameCreationRequest request = new GameCreationRequest(1L, 2, 60, 3);
        
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute("playerId")).thenReturn(2L);
        
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.createGame(request, httpRequest);
        });
        
        verify(roomRepo).findById(1L);
    }
    
    @Test
    void getGame_returnsGameWhenFound() {
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        
        ResponseEntity<Game> response = controller.getGame(1L);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testGame, response.getBody());
        verify(gameRepo).findById(1L);
    }
    
    @Test
    void getGame_throws404WhenGameNotFound() {
        when(gameRepo.findById(1L)).thenReturn(Optional.empty());
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.getGame(1L);
        });
        
        verify(gameRepo).findById(1L);
    }
    
    @Test
    void deleteGame_deletesGameWhenHost() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute("playerId")).thenReturn(1L);
        
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        ResponseEntity<Void> response = controller.deleteGame(1L, httpRequest);
        
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(gameRepo).deleteById(1L);
        verify(httpRequest).getAttribute("playerId");
    }
    
    @Test
    void deleteGame_throws401WhenNotAuthenticated() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute("playerId")).thenReturn(null);
        
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.deleteGame(1L, httpRequest);
        });
        
        verify(gameRepo).findById(1L);
        verify(roomRepo).findById(1L);
        verify(httpRequest).getAttribute("playerId");
    }
    
    @Test
    void deleteGame_throws403WhenNotHost() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getAttribute("playerId")).thenReturn(2L);
        
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(roomRepo.findById(1L)).thenReturn(Optional.of(testRoom));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.deleteGame(1L, httpRequest);
        });
        
        verify(gameRepo).findById(1L);
        verify(roomRepo).findById(1L);
        verify(httpRequest).getAttribute("playerId");
    }
    
    @Test
    void getRounds_returnsRoundsList() {
        Round round1 = new Round();
        round1.setId(1L);
        round1.setGameId(1L);
        
        Round round2 = new Round();
        round2.setId(2L);
        round2.setGameId(1L);
        
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(roundRepo.findByGameId(1L)).thenReturn(Arrays.asList(round1, round2));
        
        ResponseEntity<List<Round>> response = controller.getRounds(1L);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        verify(roundRepo).findByGameId(1L);
    }
    
    @Test
    void getRoundQuestions_returnsQuestionsList() {
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(roundRepo.findById(1L)).thenReturn(Optional.of(testRound));
        when(questionRepo.findByRoundId(1L)).thenReturn(List.of(testQuestion));
        
        ResponseEntity<List<Question>> response = controller.getRoundQuestions(1L, 1L);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(questionRepo).findByRoundId(1L);
    }
    
    @Test
    void getRoundQuestions_throws403WhenRoundNotStarted() {
        Round round = new Round();
        round.setId(1L);
        round.setGameId(1L);
        round.setCreatedAt(Instant.now().plus(Duration.ofMinutes(10)));
        
        when(gameRepo.findById(1L)).thenReturn(Optional.of(testGame));
        when(roundRepo.findById(1L)).thenReturn(Optional.of(round));
        
        assertThrows(ResponseStatusException.class, () -> {
            controller.getRoundQuestions(1L, 1L);
        });
        
        verify(gameRepo).findById(1L);
        verify(roundRepo).findById(1L);
    }
}
