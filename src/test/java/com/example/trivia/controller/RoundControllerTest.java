package com.example.trivia.controller;

import com.example.trivia.model.Game;
import com.example.trivia.model.Round;
import com.example.trivia.repository.GameRepository;
import com.example.trivia.repository.RoundRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoundControllerTest {
    @Mock
    private GameRepository gameRepo;

    @Mock
    private RoundRepository roundRepo;

    @InjectMocks
    private RoundController roundController;

    private Game testGame;

    @BeforeEach
    void setUp() {
        testGame = new Game();
        testGame.setId(1L);
        testGame.setRoomId(1L);
        testGame.setCreatedAt(Instant.now());
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

        ResponseEntity<List<Round>> response = roundController.getRounds(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        verify(gameRepo).findById(1L);
        verify(roundRepo).findByGameId(1L);
    }
}
